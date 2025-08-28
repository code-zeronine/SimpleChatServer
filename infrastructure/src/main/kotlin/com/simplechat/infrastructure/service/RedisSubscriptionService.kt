package com.simplechat.infrastructure.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.simplechat.domain.entity.ChatMessage
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy

/**
 * Redis Pub/Sub 구독 서비스
 * 
 * 채팅 메시지와 시스템 알림을 구독하고 처리하는 반응형 서비스입니다.
 */
@Service
class RedisSubscriptionService(
    private val connectionFactory: ReactiveRedisConnectionFactory,
    private val objectMapper: ObjectMapper,
    private val redisPubSubService: RedisPubSubService
) {
    
    private val logger = LoggerFactory.getLogger(RedisSubscriptionService::class.java)
    
    private lateinit var messageListenerContainer: ReactiveRedisMessageListenerContainer
    
    @PostConstruct
    fun init() {
        messageListenerContainer = ReactiveRedisMessageListenerContainer(connectionFactory)
        logger.info("Redis subscription service initialized")
    }
    
    @PreDestroy
    fun destroy() {
        if (::messageListenerContainer.isInitialized) {
            messageListenerContainer.destroy()
            logger.info("Redis subscription service destroyed")
        }
    }
    
    /**
     * 특정 채팅방의 메시지를 구독합니다.
     * 
     * @param roomId 채팅방 ID
     * @return 채팅 메시지 스트림
     */
    fun subscribeToRoom(roomId: Long): Flux<ChatMessage> {
        val channelTopic = redisPubSubService.getRoomChannelTopic(roomId)
        
        return messageListenerContainer
            .receive(channelTopic)
            .map { channelMessage ->
                deserializeChatMessage(channelMessage.message)
            }
            .onErrorResume { error ->
                logger.error("Error subscribing to room {}: {}", roomId, error.message, error)
                Flux.empty()
            }
            .doOnSubscribe { 
                logger.debug("Started subscribing to room: {}", roomId)
            }
            .doOnCancel {
                logger.debug("Cancelled subscription to room: {}", roomId)
            }
    }
    
    /**
     * 글로벌 채널의 메시지를 구독합니다.
     * 
     * @return 채팅 메시지 및 시스템 메시지 스트림
     */
    fun subscribeToGlobal(): Flux<Any> {
        val channelTopic = redisPubSubService.getGlobalChannelTopic()
        
        return messageListenerContainer
            .receive(channelTopic)
            .map { channelMessage ->
                deserializeMessage(channelMessage.message)
            }
            .onErrorResume { error ->
                logger.error("Error subscribing to global channel: {}", error.message, error)
                Flux.empty()
            }
            .doOnSubscribe { 
                logger.debug("Started subscribing to global channel")
            }
            .doOnCancel {
                logger.debug("Cancelled subscription to global channel")
            }
    }
    
    /**
     * 사용자별 개인 채널을 구독합니다.
     * 
     * @param userId 사용자 ID
     * @return 개인 메시지 스트림
     */
    fun subscribeToUser(userId: Long): Flux<Any> {
        val channelTopic = redisPubSubService.getUserChannelTopic(userId)
        
        return messageListenerContainer
            .receive(channelTopic)
            .map { channelMessage ->
                deserializeMessage(channelMessage.message)
            }
            .onErrorResume { error ->
                logger.error("Error subscribing to user {}: {}", userId, error.message, error)
                Flux.empty()
            }
            .doOnSubscribe { 
                logger.debug("Started subscribing to user: {}", userId)
            }
            .doOnCancel {
                logger.debug("Cancelled subscription to user: {}", userId)
            }
    }
    
    /**
     * 여러 채팅방을 동시에 구독합니다.
     * 
     * @param roomIds 채팅방 ID 목록
     * @return 통합된 채팅 메시지 스트림
     */
    fun subscribeToMultipleRooms(roomIds: List<Long>): Flux<Pair<Long, ChatMessage>> {
        return Flux.fromIterable(roomIds)
            .flatMap { roomId ->
                subscribeToRoom(roomId)
                    .map { message -> Pair(roomId, message) }
            }
            .doOnSubscribe { 
                logger.debug("Started subscribing to {} rooms: {}", roomIds.size, roomIds)
            }
    }
    
    /**
     * 패턴 기반으로 채널을 구독합니다.
     * 
     * @param pattern 구독할 패턴 (예: "chat:room:*")
     * @return 메시지 스트림
     */
    fun subscribeToPattern(pattern: String): Flux<Pair<String, Any>> {
        // 패턴 구독은 현재 구현에서 제외
        return Flux.empty<Pair<String, Any>>()
            .doOnSubscribe { 
                logger.debug("Pattern subscription not implemented: {}", pattern)
            }
    }
    
    /**
     * JSON 문자열을 ChatMessage 객체로 역직렬화합니다.
     * 
     * @param jsonMessage JSON 문자열
     * @return ChatMessage 객체
     */
    private fun deserializeChatMessage(jsonMessage: String): ChatMessage {
        return try {
            objectMapper.readValue(jsonMessage, ChatMessage::class.java)
        } catch (e: Exception) {
            logger.error("Failed to deserialize chat message: {}", e.message, e)
            throw IllegalArgumentException("Invalid chat message format", e)
        }
    }
    
    /**
     * JSON 문자열을 일반 객체로 역직렬화합니다.
     * 
     * @param jsonMessage JSON 문자열
     * @return 역직렬화된 객체
     */
    private fun deserializeMessage(jsonMessage: String): Any {
        return try {
            // 먼저 ChatMessage로 파싱 시도
            objectMapper.readValue(jsonMessage, ChatMessage::class.java)
        } catch (e: Exception) {
            try {
                // ChatMessage가 아니면 Map으로 파싱
                objectMapper.readValue(jsonMessage, Map::class.java)
            } catch (e2: Exception) {
                logger.warn("Failed to deserialize message, returning as string: {}", e2.message)
                jsonMessage
            }
        }
    }
    
    /**
     * 메시지 리스너를 등록합니다.
     * 
     * @param channelTopic 구독할 채널 토픽
     * @param messageHandler 메시지 처리 함수
     * @return 구독 해제를 위한 Disposable
     */
    fun registerMessageListener(
        channelTopic: ChannelTopic,
        messageHandler: (Any) -> Mono<Void>
    ): reactor.core.Disposable {
        
        return messageListenerContainer
            .receive(channelTopic)
            .map { channelMessage ->
                deserializeMessage(channelMessage.message)
            }
            .flatMap { message ->
                messageHandler(message)
                    .onErrorResume { error ->
                        logger.error("Error handling message from channel {}: {}", 
                            channelTopic.topic, error.message, error)
                        Mono.empty()
                    }
            }
            .subscribe()
    }
    
    /**
     * 현재 활성화된 구독 상태를 확인합니다.
     * 
     * @return 구독 상태 정보
     */
    fun getSubscriptionStatus(): Mono<Map<String, Any>> {
        return Mono.fromCallable {
            mapOf<String, Any>(
                "containerActive" to ::messageListenerContainer.isInitialized,
                "timestamp" to System.currentTimeMillis()
            )
        }
        .subscribeOn(Schedulers.boundedElastic())
        .doOnSuccess { status ->
            logger.debug("Subscription status retrieved: {}", status)
        }
        .doOnError { error ->
            logger.error("Failed to get subscription status: {}", error.message, error)
        }
    }
}