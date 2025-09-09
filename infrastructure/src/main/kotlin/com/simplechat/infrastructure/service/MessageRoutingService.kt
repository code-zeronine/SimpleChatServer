package com.simplechat.infrastructure.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.simplechat.domain.message.websocket.ChatWebSocketMessage
import com.simplechat.domain.message.websocket.ErrorWebSocketMessage
import com.simplechat.domain.message.websocket.JoinWebSocketMessage
import com.simplechat.domain.message.websocket.LeaveWebSocketMessage
import com.simplechat.domain.message.MessageTarget
import com.simplechat.domain.message.websocket.SystemWebSocketMessage
import com.simplechat.domain.message.websocket.TypingWebSocketMessage
import com.simplechat.domain.message.websocket.WebSocketMessage
import com.simplechat.domain.service.MessageRoutingEngine
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 메시지 라우팅 서비스
 * 
 * 기존의 여러 라우팅 및 전달 서비스들을 하나로 통합:
 * - ReactiveMessageRoutingEngine
 * - MessageRoutingIntegrationService
 * - MessageDeliveryService
 * - RedisMessageRoutingSubscriber (일부 기능)
 */
@Service
class MessageRoutingService(
    private val sessionManager: WebSocketSessionManager,
    private val redisMessageService: RedisMessageBrokerService,
    private val objectMapper: ObjectMapper
) : MessageRoutingEngine {

    private val logger = LoggerFactory.getLogger(MessageRoutingService::class.java)
    
    // 라우팅 통계
    private val routingStats = ConcurrentHashMap<String, AtomicLong>()
    
    // 전송 실패한 메시지 추적
    private val failedMessages = ConcurrentHashMap<String, FailedMessage>()
    
    // 재시도 설정
    private val maxRetries = 3
    private val initialDelay = Duration.ofMillis(500)
    private val maxDelay = Duration.ofSeconds(10)

    /**
     * 메시지 타입에 따른 라우팅 처리
     */
    override fun routeMessage(message: WebSocketMessage, sourceSessionId: String): Mono<Void> {
        val routingId = generateRoutingId()
        incrementRoutingStats("messages.total")
        
        logger.debug("Routing message {} from session {}: {}", routingId, sourceSessionId, message.type)
        
        return determineTarget(message, sourceSessionId)
            .flatMap { target -> 
                sendToTarget(target, message)
                    .retryWhen(createRetrySpec(routingId))
                    .doOnSuccess { 
                        incrementRoutingStats("messages.success")
                        logger.debug("Message {} routed successfully", routingId)
                    }
                    .doOnError { error ->
                        incrementRoutingStats("messages.failed")
                        recordFailedMessage(routingId, target, message, error)
                    }
            }
            .onErrorResume { error ->
                logger.error("Failed to route message {}: {}", routingId, error.message)
                Mono.empty()
            }
    }

    /**
     * 특정 대상에게 메시지 전송
     */
    fun sendToTarget(target: MessageTarget, message: WebSocketMessage): Mono<Void> {
        val deliveryId = generateDeliveryId()
        val startTime = System.currentTimeMillis()
        
        return performDelivery(target, message)
            .doOnSuccess { 
                val latency = System.currentTimeMillis() - startTime
                recordSuccessfulDelivery(deliveryId, target, latency)
            }
            .doOnError { error ->
                val latency = System.currentTimeMillis() - startTime  
                recordFailedDelivery(deliveryId, target, message, error, latency)
            }
    }

    /**
     * 실제 메시지 전달 수행
     */
    private fun performDelivery(target: MessageTarget, message: WebSocketMessage): Mono<Void> {
        return when (target) {
            is MessageTarget.Session -> deliverToSession(target.sessionId, message)
            is MessageTarget.User -> deliverToUser(target.userId, message)
            is MessageTarget.Users -> deliverToUsers(target.userIds, message)
            is MessageTarget.Room -> deliverToRoom(target.chatRoomId, message, target.excludeSessionId)
            is MessageTarget.Global -> deliverGlobally(message)
        }.timeout(Duration.ofSeconds(10))
    }

    /**
     * 세션에 메시지 전달
     */
    private fun deliverToSession(sessionId: String, message: WebSocketMessage): Mono<Void> {
        return Mono.fromCallable {
            sessionManager.getSessionById(sessionId)
        }.flatMap { session ->
            if (session != null && session.isOpen) {
                sendToWebSocketSession(session, message)
                    .doOnSuccess { sessionManager.updateSessionActivity(sessionId) }
            } else {
                incrementRoutingStats("delivery.session_not_found")
                Mono.empty()
            }
        }
    }

    /**
     * 사용자에게 메시지 전달
     */
    private fun deliverToUser(userId: Long, message: WebSocketMessage): Mono<Void> {
        return Mono.fromCallable {
            sessionManager.getSessionsByUserId(userId)
        }.flatMap { sessions ->
            if (sessions.isEmpty()) {
                incrementRoutingStats("delivery.user_offline")
                // 사용자가 오프라인이면 Redis를 통해 전송 (다른 인스턴스에 있을 수 있음)
                redisMessageService.publishUserMessage(userId, message).then()
            } else {
                val deliveryOperations = sessions.map { session ->
                    sendToWebSocketSession(session, message)
                        .doOnSuccess { sessionManager.updateSessionActivity(session.id) }
                        .onErrorResume { error ->
                            logger.warn("Failed to deliver to session {}: {}", session.id, error.message)
                            Mono.empty()
                        }
                }
                Mono.`when`(deliveryOperations)
            }
        }
    }

    /**
     * 여러 사용자에게 메시지 전달
     */
    private fun deliverToUsers(userIds: Set<Long>, message: WebSocketMessage): Mono<Void> {
        val deliveryOperations = userIds.map { userId ->
            deliverToUser(userId, message)
        }
        return Mono.`when`(deliveryOperations)
    }

    /**
     * 채팅방에 메시지 전달
     */
    private fun deliverToRoom(chatRoomId: String, message: WebSocketMessage, excludeSessionId: String?): Mono<Void> {
        return Mono.fromCallable {
            sessionManager.getSessionsByChatRoom(chatRoomId)
                .filter { session -> excludeSessionId == null || session.id != excludeSessionId }
        }.flatMap { sessions ->
            if (sessions.isEmpty()) {
                incrementRoutingStats("delivery.empty_room")
                // 로컬에 세션이 없으면 Redis를 통해 브로드캐스트
                redisMessageService.publishMessage("chat:room:$chatRoomId", message).then()
            } else {
                val deliveryOperations = sessions.map { session ->
                    sendToWebSocketSession(session, message)
                        .doOnSuccess { sessionManager.updateSessionActivity(session.id) }
                        .onErrorResume { error ->
                            logger.warn("Failed to deliver to session {}: {}", session.id, error.message)
                            Mono.empty()
                        }
                }
                // Redis에도 발행 (다른 인스턴스의 세션들을 위해)
                Mono.`when`(deliveryOperations.plus(
                    redisMessageService.publishMessage("chat:room:$chatRoomId", message).then()
                ))
            }
        }
    }

    /**
     * 전역 브로드캐스트
     */
    private fun deliverGlobally(message: WebSocketMessage): Mono<Void> {
        return Mono.fromCallable {
            sessionManager.getAllActiveSessions()
        }.flatMap { sessions ->
            val localDelivery = if (sessions.isNotEmpty()) {
                val deliveryOperations = sessions.map { session ->
                    sendToWebSocketSession(session, message)
                        .doOnSuccess { sessionManager.updateSessionActivity(session.id) }
                        .onErrorResume { error ->
                            logger.warn("Failed to deliver to session {}: {}", session.id, error.message)
                            Mono.empty()
                        }
                }
                Mono.`when`(deliveryOperations)
            } else {
                Mono.empty<Void>()
            }
            
            // Redis를 통한 전역 발행
            val redisDelivery = redisMessageService.publishGlobalMessage(message).then()
            
            Mono.`when`(localDelivery, redisDelivery)
        }
    }

    /**
     * WebSocket 세션에 실제 메시지 전송
     */
    private fun sendToWebSocketSession(session: WebSocketSession, message: WebSocketMessage): Mono<Void> {
        return try {
            if (!session.isOpen) {
                incrementRoutingStats("delivery.closed_session")
                return Mono.error(RuntimeException("Session is closed: ${session.id}"))
            }
            
            val messageJson = objectMapper.writeValueAsString(message)
            session.send(Mono.just(session.textMessage(messageJson)))
                .timeout(Duration.ofSeconds(5))
                .doOnSuccess { 
                    incrementRoutingStats("delivery.success")
                    logger.debug("Message delivered to session: {}", session.id)
                }
                .doOnError { error ->
                    incrementRoutingStats("delivery.failed")
                    logger.warn("Failed to deliver to session {}: {}", session.id, error.message)
                }
        } catch (e: Exception) {
            incrementRoutingStats("delivery.serialization_error")
            Mono.error(RuntimeException("Failed to serialize message", e))
        }
    }

    // MessageRoutingEngine 인터페이스 구현
    override fun broadcastToRoom(chatRoomId: String, message: WebSocketMessage, excludeSessionId: String?): Mono<Void> {
        return deliverToRoom(chatRoomId, message, excludeSessionId)
    }

    override fun sendToUser(userId: Long, message: WebSocketMessage): Mono<Void> {
        return deliverToUser(userId, message)
    }

    override fun sendToUsers(userIds: Set<Long>, message: WebSocketMessage): Mono<Void> {
        return deliverToUsers(userIds, message)
    }

    override fun sendToSession(sessionId: String, message: WebSocketMessage): Mono<Void> {
        return deliverToSession(sessionId, message)
    }

    override fun broadcastGlobally(message: WebSocketMessage): Mono<Void> {
        return deliverGlobally(message)
    }

    override fun retryFailedMessage(message: WebSocketMessage, target: MessageTarget, maxRetries: Int): Mono<Void> {
        return sendToTarget(target, message)
            .retryWhen(Retry.backoff(maxRetries.toLong(), initialDelay)
                .maxBackoff(maxDelay)
                .filter { isRetryableError(it) }
            )
    }

    /**
     * 메시지 대상 결정
     */
    private fun determineTarget(message: WebSocketMessage, sourceSessionId: String): Mono<MessageTarget> {
        return Mono.fromCallable {
            when (message) {
                is ChatWebSocketMessage -> MessageTarget.Room(message.roomId.toString(), sourceSessionId)
                is JoinWebSocketMessage -> MessageTarget.Room(message.roomId.toString(), null)
                is LeaveWebSocketMessage -> MessageTarget.Room(message.roomId.toString(), null)
                is TypingWebSocketMessage -> MessageTarget.Room(message.roomId.toString(), sourceSessionId)
                is SystemWebSocketMessage -> MessageTarget.Global
                is ErrorWebSocketMessage -> MessageTarget.Session(sourceSessionId)
                else -> MessageTarget.Session(sourceSessionId)
            }
        }
    }

    /**
     * 재시도 정책 생성
     */
    private fun createRetrySpec(routingId: String): Retry {
        return Retry.backoff(maxRetries.toLong(), initialDelay)
            .maxBackoff(maxDelay)
            .filter { throwable -> isRetryableError(throwable) }
            .doBeforeRetry { signal ->
                incrementRoutingStats("messages.retries")
                logger.warn("Retrying message routing {} (attempt {}): {}", 
                    routingId, signal.totalRetries() + 1, signal.failure().message)
            }
    }

    /**
     * 재시도 가능한 오류 판별
     */
    private fun isRetryableError(throwable: Throwable): Boolean {
        return when (throwable) {
            is java.util.concurrent.TimeoutException -> true
            is java.io.IOException -> true
            is RuntimeException -> throwable.message?.contains("closed") != true
            else -> false
        }
    }

    /**
     * 성공적인 전달 기록
     */
    private fun recordSuccessfulDelivery(deliveryId: String, target: MessageTarget, latencyMs: Long) {
        logger.debug("Message delivery successful: {} to {} in {}ms", deliveryId, target, latencyMs)
        incrementRoutingStats("delivery.total_success")
    }

    /**
     * 실패한 전달 기록
     */
    private fun recordFailedDelivery(deliveryId: String, target: MessageTarget, message: WebSocketMessage, 
                                   error: Throwable, latencyMs: Long) {
        val failedMessage = FailedMessage(
            id = deliveryId,
            target = target,
            message = message,
            error = error.message ?: "Unknown error",
            failedAt = Instant.now(),
            retryCount = 0
        )
        
        failedMessages[deliveryId] = failedMessage
        incrementRoutingStats("delivery.total_failed")
        
        logger.error("Message delivery failed: {} to {} after {}ms: {}", 
            deliveryId, target, latencyMs, error.message)
    }

    /**
     * 실패한 메시지 기록
     */
    private fun recordFailedMessage(routingId: String, target: MessageTarget, message: WebSocketMessage, error: Throwable) {
        val failedMessage = FailedMessage(
            id = routingId,
            target = target,
            message = message,
            error = error.message ?: "Unknown error",
            failedAt = Instant.now(),
            retryCount = 0
        )
        
        failedMessages[routingId] = failedMessage
        logger.error("Message routing failed: {} to {}: {}", routingId, target, error.message)
    }

    /**
     * 라우팅 통계 증가
     */
    private fun incrementRoutingStats(key: String) {
        routingStats.computeIfAbsent(key) { AtomicLong(0) }.incrementAndGet()
    }

    /**
     * ID 생성
     */
    private fun generateRoutingId(): String = "route_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 8)}"
    private fun generateDeliveryId(): String = "delivery_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 8)}"

    /**
     * 통계 조회
     */
    fun getRoutingStatistics(): Map<String, Any> {
        val stats = routingStats.mapValues { it.value.get() }
        return mapOf(
            "routing_stats" to stats,
            "failed_messages_count" to failedMessages.size,
            "session_manager_stats" to sessionManager.getCacheStatistics()
        )
    }

    /**
     * 헬스 체크
     */
    fun healthCheck(): Mono<Map<String, Any>> {
        return Mono.just(
            mapOf(
                "status" to "UP",
                "active_sessions" to sessionManager.getAllActiveSessions().size,
                "failed_messages" to failedMessages.size,
                "total_routed_messages" to (routingStats["messages.total"]?.get() ?: 0L),
                "success_rate" to calculateSuccessRate()
            )
        )
    }

    /**
     * 성공률 계산
     */
    private fun calculateSuccessRate(): Double {
        val total = routingStats["messages.total"]?.get() ?: 0L
        val success = routingStats["messages.success"]?.get() ?: 0L
        return if (total > 0) (success.toDouble() / total * 100) else 0.0
    }

    /**
     * 통계 리셋
     */
    fun resetStatistics() {
        routingStats.clear()
        failedMessages.clear()
        logger.info("Message routing statistics reset")
    }
}
