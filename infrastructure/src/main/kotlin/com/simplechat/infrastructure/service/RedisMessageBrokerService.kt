package com.simplechat.infrastructure.service

import com.simplechat.domain.entity.ChatMessage
import com.simplechat.infrastructure.config.ChannelTopicFactory
import org.slf4j.LoggerFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import java.util.concurrent.ConcurrentHashMap
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy

/**
 * Redis 메시지 브로커 서비스
 * 
 * Redis Pub/Sub 기능을 통합하여 채팅 시스템의 실시간 메시징을 관리합니다.
 * 발행(publish), 구독(subscribe), 채널 관리를 담당합니다.
 */
@Service
class RedisMessageBrokerService(
    private val redisPubSubService: RedisPubSubService,
    private val redisSubscriptionService: RedisSubscriptionService,
    private val messageSerializationService: RedisMessageSerializationService,
    private val channelTopicFactory: ChannelTopicFactory
) {
    
    private val logger = LoggerFactory.getLogger(RedisMessageBrokerService::class.java)
    
    // 활성 구독을 추적하기 위한 맵
    private val activeSubscriptions = ConcurrentHashMap<String, reactor.core.Disposable>()
    
    // 메시지 스트림을 관리하기 위한 싱크
    private val messageStreamSinks = ConcurrentHashMap<String, Sinks.Many<Any>>()
    
    @PostConstruct
    fun init() {
        logger.info("Redis message broker service initialized")
    }
    
    @PreDestroy
    fun destroy() {
        // 모든 활성 구독 해제
        activeSubscriptions.values.forEach { subscription ->
            if (!subscription.isDisposed) {
                subscription.dispose()
            }
        }
        
        // 모든 메시지 스트림 완료
        messageStreamSinks.values.forEach { sink ->
            sink.tryEmitComplete()
        }
        
        activeSubscriptions.clear()
        messageStreamSinks.clear()
        
        logger.info("Redis message broker service destroyed")
    }
    
    /**
     * 채팅방에 메시지를 발행합니다.
     * 
     * @param roomId 채팅방 ID
     * @param message 채팅 메시지
     * @return 구독자 수를 담은 Mono
     */
    fun publishChatMessage(roomId: Long, message: ChatMessage): Mono<Long> {
        return redisPubSubService.publishToRoom(roomId, message)
            .doOnSuccess { subscriberCount ->
                logger.debug("Chat message published to room {}: {} subscribers", 
                    roomId, subscriberCount)
            }
    }
    
    /**
     * 채팅방 메시지 스트림을 구독합니다.
     * 
     * @param roomId 채팅방 ID
     * @return 채팅 메시지 스트림
     */
    fun subscribeChatRoom(roomId: Long): Flux<ChatMessage> {
        val streamKey = "room:$roomId"
        
        return getOrCreateMessageStream(streamKey) {
            redisSubscriptionService.subscribeToRoom(roomId)
                .map { it as Any }
        }.cast(ChatMessage::class.java)
    }
    
    /**
     * 사용자별 개인 메시지 스트림을 구독합니다.
     * 
     * @param userId 사용자 ID
     * @return 개인 메시지 스트림
     */
    fun subscribeUserChannel(userId: Long): Flux<Any> {
        val streamKey = "user:$userId"
        
        return getOrCreateMessageStream(streamKey) {
            redisSubscriptionService.subscribeToUser(userId)
        }
    }
    
    /**
     * 글로벌 채널을 구독합니다.
     * 
     * @return 글로벌 메시지 스트림
     */
    fun subscribeGlobalChannel(): Flux<Any> {
        val streamKey = "global"
        
        return getOrCreateMessageStream(streamKey) {
            redisSubscriptionService.subscribeToGlobal()
        }
    }
    
    /**
     * 여러 채팅방을 동시에 구독합니다.
     * 
     * @param roomIds 채팅방 ID 목록
     * @return 통합된 메시지 스트림 (채팅방 ID와 메시지 쌍)
     */
    fun subscribeMultipleRooms(roomIds: List<Long>): Flux<Pair<Long, ChatMessage>> {
        return redisSubscriptionService.subscribeToMultipleRooms(roomIds)
            .doOnSubscribe { 
                logger.debug("Started subscribing to multiple rooms: {}", roomIds)
            }
    }
    
    /**
     * 시스템 메시지를 브로드캐스트합니다.
     * 
     * @param message 시스템 메시지
     * @param excludeUserId 제외할 사용자 ID (선택적)
     * @return 작업 완료 신호
     */
    fun broadcastSystemMessage(message: String, excludeUserId: Long? = null): Mono<Void> {
        return messageSerializationService.createSystemMessage(message, 
            excludeUserId?.let { mapOf("excludeUserId" to it) })
            .flatMap { jsonMessage ->
                redisPubSubService.publishToUser(excludeUserId ?: 0L, jsonMessage)
            }
            .then()
            .doOnSuccess {
                logger.info("System message broadcasted: {}", message)
            }
    }
    
    /**
     * 사용자에게 개인 알림을 발송합니다.
     * 
     * @param userId 사용자 ID
     * @param message 알림 메시지
     * @param notificationType 알림 타입
     * @return 구독자 수를 담은 Mono
     */
    fun sendUserNotification(
        userId: Long, 
        message: String, 
        notificationType: String = "INFO"
    ): Mono<Long> {
        return messageSerializationService.createUserNotification(userId, message, notificationType)
            .flatMap { jsonMessage ->
                redisPubSubService.publishToUser(userId, jsonMessage)
            }
            .doOnSuccess { subscriberCount ->
                logger.debug("User notification sent to {}: {} subscribers", userId, subscriberCount)
            }
    }
    
    /**
     * 채팅방 이벤트를 발행합니다.
     * 
     * @param roomId 채팅방 ID
     * @param eventType 이벤트 타입 (JOIN, LEAVE, etc.)
     * @param userId 사용자 ID
     * @param additionalData 추가 데이터
     * @return 구독자 수를 담은 Mono
     */
    fun publishRoomEvent(
        roomId: Long,
        eventType: String,
        userId: Long,
        additionalData: Map<String, Any>? = null
    ): Mono<Long> {
        return messageSerializationService.createRoomEvent(roomId, eventType, userId, additionalData)
            .flatMap { jsonMessage ->
                redisPubSubService.publishToUser(userId, jsonMessage)
            }
            .doOnSuccess { subscriberCount ->
                logger.debug("Room event published to {}: {} by user {}, {} subscribers", 
                    roomId, eventType, userId, subscriberCount)
            }
    }
    
    /**
     * 특정 구독을 해제합니다.
     * 
     * @param streamKey 스트림 키
     */
    fun unsubscribe(streamKey: String) {
        activeSubscriptions[streamKey]?.let { subscription ->
            if (!subscription.isDisposed) {
                subscription.dispose()
                logger.debug("Unsubscribed from stream: {}", streamKey)
            }
        }
        activeSubscriptions.remove(streamKey)
        
        messageStreamSinks[streamKey]?.let { sink ->
            sink.tryEmitComplete()
        }
        messageStreamSinks.remove(streamKey)
    }
    
    /**
     * 채팅방 구독을 해제합니다.
     * 
     * @param roomId 채팅방 ID
     */
    fun unsubscribeFromRoom(roomId: Long) {
        unsubscribe("room:$roomId")
    }
    
    /**
     * 사용자 채널 구독을 해제합니다.
     * 
     * @param userId 사용자 ID
     */
    fun unsubscribeFromUser(userId: Long) {
        unsubscribe("user:$userId")
    }
    
    /**
     * 글로벌 채널 구독을 해제합니다.
     */
    fun unsubscribeFromGlobal() {
        unsubscribe("global")
    }
    
    /**
     * 현재 활성화된 구독 정보를 반환합니다.
     * 
     * @return 구독 정보를 담은 Mono
     */
    fun getActiveSubscriptions(): Mono<Map<String, Any>> {
        return Mono.fromCallable {
            mapOf<String, Any>(
                "activeSubscriptionsCount" to activeSubscriptions.size,
                "activeStreamsCount" to messageStreamSinks.size,
                "subscriptionKeys" to activeSubscriptions.keys.toList(),
                "timestamp" to System.currentTimeMillis()
            )
        }
        .doOnSuccess { info ->
            logger.debug("Active subscriptions info retrieved: {} subscriptions, {} streams", 
                activeSubscriptions.size, messageStreamSinks.size)
        }
    }
    
    /**
     * 메시지 스트림을 가져오거나 생성합니다.
     * 
     * @param streamKey 스트림 키
     * @param streamFactory 스트림 생성 팩토리
     * @return 메시지 스트림
     */
    private fun getOrCreateMessageStream(
        streamKey: String, 
        streamFactory: () -> Flux<Any>
    ): Flux<Any> {
        
        // 기존 스트림이 있다면 재사용
        messageStreamSinks[streamKey]?.let { existingSink ->
            return existingSink.asFlux()
        }
        
        // 새 스트림 생성
        val sink = Sinks.many().multicast().onBackpressureBuffer<Any>()
        messageStreamSinks[streamKey] = sink
        
        // Redis 구독 시작
        val subscription = streamFactory()
            .doOnNext { message ->
                val result = sink.tryEmitNext(message)
                if (result.isFailure) {
                    logger.warn("Failed to emit message to stream {}: {}", streamKey, result)
                }
            }
            .doOnError { error ->
                logger.error("Error in message stream {}: {}", streamKey, error.message, error)
                sink.tryEmitError(error)
            }
            .doOnComplete {
                logger.debug("Message stream {} completed", streamKey)
                sink.tryEmitComplete()
            }
            .subscribe()
        
        activeSubscriptions[streamKey] = subscription
        logger.debug("Created new message stream: {}", streamKey)
        
        return sink.asFlux()
            .doOnCancel {
                logger.debug("Message stream cancelled: {}", streamKey)
                unsubscribe(streamKey)
            }
    }
}