package com.simplechat.infrastructure.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.simplechat.domain.entity.ChatMessage
import com.simplechat.domain.entity.MessageType
import com.simplechat.infrastructure.service.ChatRoomSubscriptionService
import com.simplechat.infrastructure.service.RedisMessageBrokerService
import com.simplechat.infrastructure.service.WebSocketSessionCacheService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.time.LocalDateTime

/**
 * WebSocket 메시지 처리 핸들러
 * 
 * WebSocket을 통해 수신된 메시지를 처리하고 Redis 메시지 브로커와 연동하여
 * 실시간 채팅 기능을 제공합니다.
 */
@Component
class WebSocketMessageHandler(
    private val redisMessageBroker: RedisMessageBrokerService,
    private val sessionCacheService: WebSocketSessionCacheService,
    private val sessionEventHandler: WebSocketSessionEventHandler,
    private val roomSubscriptionService: ChatRoomSubscriptionService,
    private val objectMapper: ObjectMapper
) {
    
    private val logger = LoggerFactory.getLogger(WebSocketMessageHandler::class.java)
    
    /**
     * WebSocket 세션을 처리합니다.
     * 
     * @param session WebSocket 세션
     * @param userId 사용자 ID
     * @return 세션 처리 Mono
     */
    fun handleSession(session: WebSocketSession, userId: Long): Mono<Void> {
        logger.info("Starting WebSocket session for user {}: {}", userId, session.id)
        
        // 세션 연결 이벤트 처리
        return sessionEventHandler.handleSessionConnected(
            sessionId = session.id,
            userId = userId,
            remoteAddress = session.handshakeInfo.remoteAddress?.toString(),
            userAgent = session.handshakeInfo.headers.getFirst("User-Agent"),
            attributes = extractSessionAttributes(session)
        ).then(
            // 입력 메시지 처리와 출력 메시지 전송을 병행
            Mono.zip(
                handleIncomingMessages(session, userId),
                sendOutgoingMessages(session, userId)
            ).then()
        )
        .doOnSuccess {
            logger.info("WebSocket session completed for user {}: {}", userId, session.id)
        }
        .doOnError { error ->
            logger.error("Error in WebSocket session for user {}: {}", userId, error.message, error)
        }
        .doFinally { signal ->
            // 세션 연결 해제 시 모든 채팅방 구독 해제
            roomSubscriptionService.unsubscribeUserFromAllRooms(userId)
                .flatMap {
                    // 세션 연결 해제 이벤트 처리
                    sessionEventHandler.handleSessionDisconnected(session.id)
                }
                .subscribe(
                    { logger.debug("Session and subscription cleanup completed for user {}", userId) },
                    { error -> logger.error("Error during cleanup for user {}: {}", userId, error.message) }
                )
        }
    }
    
    /**
     * 수신된 WebSocket 메시지를 처리합니다.
     * 
     * @param session WebSocket 세션
     * @param userId 사용자 ID
     * @return 메시지 처리 완료 Mono
     */
    private fun handleIncomingMessages(session: WebSocketSession, userId: Long): Mono<Void> {
        return session.receive()
            .map { it.payloadAsText }
            .doOnNext { messageText ->
                logger.trace("Received message from user {}: {}", userId, messageText)
                // 세션 활동 업데이트
                sessionCacheService.updateSessionHeartbeat(session.id).subscribe()
            }
            .flatMap { messageText ->
                processIncomingMessage(messageText, userId)
            }
            .doOnError { error ->
                logger.error("Error processing incoming messages for user {}: {}", userId, error.message, error)
            }
            .then()
    }
    
    /**
     * 나가는 WebSocket 메시지를 전송합니다.
     * 
     * @param session WebSocket 세션
     * @param userId 사용자 ID
     * @return 메시지 전송 완료 Mono
     */
    private fun sendOutgoingMessages(session: WebSocketSession, userId: Long): Mono<Void> {
        // 사용자 채널과 글로벌 채널을 구독
        val userMessages = redisMessageBroker.subscribeUserChannel(userId)
            .doOnNext { message ->
                logger.trace("Received user message for {}: {}", userId, message)
            }
        
        val globalMessages = redisMessageBroker.subscribeGlobalChannel()
            .doOnNext { message ->
                logger.trace("Received global message: {}", message)
            }
        
        // 두 스트림을 합쳐서 전송
        return session.send(
            Flux.merge(userMessages, globalMessages)
                .map { message ->
                    val messageText = when (message) {
                        is String -> message
                        else -> objectMapper.writeValueAsString(message)
                    }
                    session.textMessage(messageText)
                }
                .doOnNext { webSocketMessage ->
                    logger.trace("Sending WebSocket message to user {}: {}", userId, webSocketMessage.payloadAsText)
                }
                .doOnError { error ->
                    logger.error("Error sending outgoing messages to user {}: {}", userId, error.message, error)
                }
        )
        .then(Mono.fromRunnable<Void> {
            logger.debug("Outgoing message stream completed for user {}", userId)
        })
    }
    
    /**
     * 수신된 메시지를 처리합니다.
     * 
     * @param messageText 메시지 텍스트
     * @param userId 사용자 ID
     * @return 메시지 처리 결과 Mono
     */
    private fun processIncomingMessage(messageText: String, userId: Long): Mono<Void> {
        return Mono.fromCallable {
            objectMapper.readValue(messageText, IncomingWebSocketMessage::class.java)
        }
        .flatMap { message ->
            when (message.type) {
                "CHAT_MESSAGE" -> handleChatMessage(message, userId)
                "JOIN_ROOM" -> handleJoinRoom(message, userId)
                "LEAVE_ROOM" -> handleLeaveRoom(message, userId)
                "HEARTBEAT" -> handleHeartbeat(message, userId)
                else -> {
                    logger.warn("Unknown message type from user {}: {}", userId, message.type)
                    Mono.empty()
                }
            }
        }
        .doOnError { error ->
            logger.error("Error processing message from user {}: {}", userId, error.message, error)
        }
        .onErrorResume { Mono.empty() }
    }
    
    /**
     * 채팅 메시지를 처리합니다.
     */
    private fun handleChatMessage(message: IncomingWebSocketMessage, userId: Long): Mono<Void> {
        val roomId = message.roomId ?: return Mono.empty()
        val content = message.content ?: return Mono.empty()
        
        val chatMessage = ChatMessage(
            id = null,
            roomId = roomId,
            userId = userId,
            content = content,
            timestamp = LocalDateTime.now(),
            messageType = MessageType.TEXT
        )
        
        return redisMessageBroker.publishChatMessage(roomId, chatMessage)
            .doOnSuccess { subscriberCount ->
                logger.debug("Chat message published by user {} to room {}: {} subscribers", 
                    userId, roomId, subscriberCount)
            }
            .then()
    }
    
    /**
     * 채팅방 입장을 처리합니다.
     */
    private fun handleJoinRoom(message: IncomingWebSocketMessage, userId: Long): Mono<Void> {
        val roomId = message.roomId ?: return Mono.empty()
        
        return roomSubscriptionService.subscribeUserToRoom(userId, roomId)
            .doOnSuccess { isNewSubscription ->
                if (isNewSubscription) {
                    logger.debug("User {} successfully joined room {}", userId, roomId)
                } else {
                    logger.debug("User {} was already in room {}", userId, roomId)
                }
            }
            .then()
    }
    
    /**
     * 채팅방 퇴장을 처리합니다.
     */
    private fun handleLeaveRoom(message: IncomingWebSocketMessage, userId: Long): Mono<Void> {
        val roomId = message.roomId ?: return Mono.empty()
        
        return roomSubscriptionService.unsubscribeUserFromRoom(userId, roomId)
            .doOnSuccess { wasSubscribed ->
                if (wasSubscribed) {
                    logger.debug("User {} successfully left room {}", userId, roomId)
                } else {
                    logger.debug("User {} was not subscribed to room {}", userId, roomId)
                }
            }
            .then()
    }
    
    /**
     * 하트비트를 처리합니다.
     */
    private fun handleHeartbeat(message: IncomingWebSocketMessage, userId: Long): Mono<Void> {
        return sessionCacheService.updateSessionHeartbeat(message.sessionId ?: "")
            .doOnSuccess { success ->
                if (success) {
                    logger.trace("Heartbeat processed for user {}", userId)
                } else {
                    logger.debug("Session not found for heartbeat from user {}", userId)
                }
            }
            .then()
    }
    
    /**
     * WebSocket 세션에서 속성을 추출합니다.
     */
    private fun extractSessionAttributes(session: WebSocketSession): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        
        session.handshakeInfo.headers.forEach { key, values ->
            when (key.lowercase()) {
                "x-forwarded-for" -> attributes["clientIp"] = values.firstOrNull() ?: ""
                "x-real-ip" -> attributes["realIp"] = values.firstOrNull() ?: ""
                "origin" -> attributes["origin"] = values.firstOrNull() ?: ""
                "sec-websocket-protocol" -> attributes["protocol"] = values.firstOrNull() ?: ""
            }
        }
        
        attributes["webSocketVersion"] = session.handshakeInfo.headers
            .getFirst("Sec-WebSocket-Version") ?: "unknown"
        
        return attributes
    }
}

/**
 * 수신된 WebSocket 메시지 데이터 클래스
 */
data class IncomingWebSocketMessage(
    val type: String,
    val roomId: Long? = null,
    val content: String? = null,
    val sessionId: String? = null,
    val timestamp: Instant? = null,
    val metadata: Map<String, Any>? = null
)