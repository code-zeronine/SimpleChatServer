package com.simplechat.infrastructure.messaging.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.simplechat.domain.message.websocket.WebSocketMessage
import com.simplechat.domain.service.MessageBrokerDomainService
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Redis 메시지 브로커 서비스
 * 
 * 기존의 여러 Redis 서비스들을 하나로 통합:
 * - RedisMessageBroker
 * - RedisMessageBrokerService  
 * - RedisPubSubService
 * - RedisSubscriptionService
 * - RoomSubscriptionService
 * - ChatRoomSubscriptionService
 * - RedisMessageSerializationService
 */
@Service
class RedisMessageBrokerService(
    @Qualifier("reactiveStringRedisTemplate") private val redisTemplate: ReactiveRedisTemplate<String, String>,
    private val messageListenerContainer: ReactiveRedisMessageListenerContainer,
    private val objectMapper: ObjectMapper
) : MessageBrokerDomainService { // Implement the interface

    private val logger = LoggerFactory.getLogger(RedisMessageBrokerService::class.java)
    
    // 구독 관리
    private val activeSubscriptions = ConcurrentHashMap<String, ChannelTopic>()
    private val subscriberCount = ConcurrentHashMap<String, AtomicLong>()
    
    // 통계
    private val publishedMessages = AtomicLong(0)
    private val receivedMessages = AtomicLong(0)
    private val failedOperations = AtomicLong(0)

    @PostConstruct
    fun initialize() {
        logger.info("Unified Redis Message Service initialized")
    }

    @PreDestroy
    fun cleanup() {
        activeSubscriptions.clear()
        subscriberCount.clear()
        logger.info("Unified Redis Message Service cleaned up")
    }

    /**
     * 메시지 브로드캐스트
     */
    override fun broadcast(chatRoomId: String, message: WebSocketMessage) { // Changed return type to Unit
        val channel = "chat:room:$chatRoomId"
        publishMessage(channel, message)
            .doOnSuccess { publishedMessages.incrementAndGet() }
            .doOnError { error ->
                failedOperations.incrementAndGet()
                logger.error("Failed to broadcast message to room {}: {}", chatRoomId, error.message)
            }
            .subscribeOn(Schedulers.parallel())
            .subscribe() // Subscribe to trigger the Mono
    }

    /**
     * 특정 사용자에게 메시지를 전송합니다.
     */
    override fun sendToUser(userId: Long, message: WebSocketMessage) { // Changed return type to Unit
        val channel = "chat:user:$userId"
        publishMessage(channel, message)
            .doOnSuccess { publishedMessages.incrementAndGet() }
            .doOnError { error ->
                failedOperations.incrementAndGet()
                logger.error("Failed to send message to user {}: {}", userId, error.message)
            }
            .subscribeOn(Schedulers.parallel())
            .subscribe() // Subscribe to trigger the Mono
    }

    /**
     * 특정 채널에 메시지 발행
     */
    fun publishMessage(channel: String, message: WebSocketMessage): Mono<Long> {
        return try {
            val messageJson = objectMapper.writeValueAsString(message)
            redisTemplate.convertAndSend(channel, messageJson)
                .doOnSuccess {
                    logger.debug("Message published to channel {}: {}", channel, message.type)
                }
                .onErrorResume { error ->
                    logger.error("Failed to publish message to channel {}: {}", channel, error.message)
                    failedOperations.incrementAndGet()
                    Mono.just(0L)
                }
        } catch (e: Exception) {
            logger.error("Failed to serialize message for channel {}: {}", channel, e.message)
            failedOperations.incrementAndGet()
            Mono.just(0L)
        }
    }

    /**
     * 채널 구독
     */
    fun subscribeToChannel(channel: String): Flux<WebSocketMessage> {
        val topic = ChannelTopic.of(channel)
        activeSubscriptions[channel] = topic
        subscriberCount.computeIfAbsent(channel) { AtomicLong(0) }.incrementAndGet()
        
        logger.info("Subscribing to channel: {}", channel)
        
        return messageListenerContainer
            .receive(topic)
            .map { message ->
                receivedMessages.incrementAndGet()
                deserializeMessage(message.message)
            }
            .onErrorContinue { error, _ ->
                failedOperations.incrementAndGet()
                logger.error("Error processing message from channel {}: {}", channel, error.message)
            }
    }

    /**
     * 채팅방 구독
     */
    fun subscribeToChatRoom(chatRoomId: String): Flux<WebSocketMessage> {
        val channel = "chat:room:$chatRoomId"
        return subscribeToChannel(channel)
    }

    /**
     * 채널 구독 해제
     */
    fun unsubscribeFromChannel(channel: String): Mono<Void> {
        return Mono.fromRunnable {
            activeSubscriptions.remove(channel)
            val count = subscriberCount[channel]?.decrementAndGet() ?: 0
            
            if (count <= 0) {
                subscriberCount.remove(channel)
                logger.info("All subscribers removed from channel: {}", channel)
            }
            
            logger.debug("Unsubscribed from channel: {} (remaining: {})", channel, count)
        }
    }

    /**
     * 채팅방 구독 해제
     */
    fun unsubscribeFromChatRoom(chatRoomId: String): Mono<Void> {
        val channel = "chat:room:$chatRoomId"
        return unsubscribeFromChannel(channel)
    }

    /**
     * 글로벌 메시지 발행
     */
    fun publishGlobalMessage(message: WebSocketMessage): Mono<Long> {
        return publishMessage("chat:global", message)
    }

    /**
     * 사용자별 메시지 발행
     */
    fun publishUserMessage(userId: Long, message: WebSocketMessage): Mono<Long> {
        return publishMessage("chat:user:$userId", message)
    }

    /**
     * 메시지 역직렬화
     */
    private fun deserializeMessage(messageData: String): WebSocketMessage {
        return try {
            objectMapper.readValue(messageData, WebSocketMessage::class.java)
        } catch (e: Exception) {
            logger.error("Failed to deserialize message: {}", e.message)
            failedOperations.incrementAndGet()
            throw RuntimeException("Message deserialization failed", e)
        }
    }

    /**
     * 연결 상태 확인
     */
    fun isConnected(): Mono<Boolean> {
        return redisTemplate.hasKey("ping:test")
            .timeout(Duration.ofSeconds(2))
            .onErrorReturn(false)
    }

    /**
     * 통계 정보 조회
     */
    fun getStatistics(): Map<String, Any> {
        return mapOf(
            "publishedMessages" to publishedMessages.get(),
            "receivedMessages" to receivedMessages.get(),
            "failedOperations" to failedOperations.get(),
            "activeSubscriptions" to activeSubscriptions.size,
            "totalSubscribers" to subscriberCount.values.sumOf { it.get() },
            "channels" to activeSubscriptions.keys.toList()
        )
    }

    /**
     * 헬스 체크
     */
    fun healthCheck(): Mono<Map<String, Any>> {
        return isConnected()
            .map { connected ->
                mapOf(
                    "status" to if (connected) "UP" else "DOWN",
                    "redis_connected" to connected,
                    "active_subscriptions" to activeSubscriptions.size,
                    "total_published" to publishedMessages.get(),
                    "total_received" to receivedMessages.get(),
                    "failed_operations" to failedOperations.get()
                )
            }
    }

    /**
     * 모든 활성 채널 조회
     */
    fun getActiveChannels(): Set<String> {
        return activeSubscriptions.keys.toSet()
    }

    /**
     * 특정 채널의 구독자 수 조회
     */
    fun getSubscriberCount(channel: String): Long {
        return subscriberCount[channel]?.get() ?: 0
    }
}