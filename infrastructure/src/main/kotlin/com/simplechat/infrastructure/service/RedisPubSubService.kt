package com.simplechat.infrastructure.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.simplechat.domain.entity.ChatMessage
import com.simplechat.infrastructure.config.MessagingConfig
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * Redis Pub/Sub 메시지 발행/구독 서비스
 * 
 * 채팅 메시지의 실시간 브로드캐스팅을 위해 Redis Pub/Sub 패턴을 구현합니다.
 */
@Service
class RedisPubSubService(
    private val reactiveRedisTemplate: ReactiveRedisTemplate<String, Any>,
    private val objectMapper: ObjectMapper,
    private val channelTopicFactory: MessagingConfig.ChannelTopicFactory
) {
    
    private val logger = LoggerFactory.getLogger(RedisPubSubService::class.java)
    
    
    /**
     * 특정 채팅방에 메시지를 발행합니다.
     * 
     * @param roomId 채팅방 ID
     * @param message 발행할 채팅 메시지
     * @return 메시지를 받은 구독자 수
     */
    fun publishToRoom(roomId: Long, message: ChatMessage): Mono<Long> {
        val channelTopic = channelTopicFactory.createRoomChannelTopic(roomId)
        
        return publishMessage(channelTopic.topic, message)
            .doOnSuccess { subscriberCount ->
                logger.debug("Message published to room {}: {} subscribers notified", 
                    roomId, subscriberCount)
            }
            .doOnError { error ->
                logger.error("Failed to publish message to room {}: {}", roomId, error.message, error)
            }
    }
    
    /**
     * 글로벌 채널에 메시지를 발행합니다.
     * 
     * @param message 발행할 채팅 메시지
     * @return 메시지를 받은 구독자 수
     */
    fun publishToGlobal(message: ChatMessage): Mono<Long> {
        val channelTopic = channelTopicFactory.createGlobalChannelTopic()
        
        return publishMessage(channelTopic.topic, message)
            .doOnSuccess { subscriberCount ->
                logger.debug("Message published to global channel: {} subscribers notified", 
                    subscriberCount)
            }
            .doOnError { error ->
                logger.error("Failed to publish message to global channel: {}", error.message, error)
            }
    }
    
    /**
     * 사용자별 개인 채널에 메시지를 발행합니다.
     * 
     * @param userId 사용자 ID
     * @param message 발행할 메시지
     * @return 메시지를 받은 구독자 수
     */
    fun publishToUser(userId: Long, message: Any): Mono<Long> {
        val channelTopic = channelTopicFactory.createUserPrivateChannelTopic(userId)
        
        return publishMessage(channelTopic.topic, message)
            .doOnSuccess { subscriberCount ->
                logger.debug("Message published to user {}: {} subscribers notified", 
                    userId, subscriberCount)
            }
            .doOnError { error ->
                logger.error("Failed to publish message to user {}: {}", userId, error.message, error)
            }
    }
    
    /**
     * 메시지를 JSON으로 직렬화하여 지정된 채널에 발행합니다.
     * 
     * @param channelName 채널명
     * @param message 발행할 메시지
     * @return 메시지를 받은 구독자 수
     */
    private fun publishMessage(channelName: String, message: Any): Mono<Long> {
        return Mono.fromCallable {
                objectMapper.writeValueAsString(message)
            }
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap { jsonMessage ->
                reactiveRedisTemplate.convertAndSend(channelName, jsonMessage)
            }
            .doOnSubscribe { 
                logger.trace("Publishing message to channel: {}", channelName)
            }
            .onErrorResume { error ->
                logger.error("Error serializing/publishing message to channel {}: {}", 
                    channelName, error.message, error)
                Mono.just(0L)
            }
    }
    
    /**
     * 채팅방 ChannelTopic을 생성합니다.
     * 
     * @param roomId 채팅방 ID
     * @return ChannelTopic 객체
     */
    fun getRoomChannelTopic(roomId: Long): ChannelTopic {
        return channelTopicFactory.createRoomChannelTopic(roomId)
    }
    
    /**
     * 글로벌 채널 ChannelTopic을 반환합니다.
     */
    fun getGlobalChannelTopic(): ChannelTopic {
        return channelTopicFactory.createGlobalChannelTopic()
    }
    
    /**
     * 사용자 개인 채널 ChannelTopic을 생성합니다.
     * 
     * @param userId 사용자 ID
     * @return ChannelTopic 객체
     */
    fun getUserChannelTopic(userId: Long): ChannelTopic {
        return channelTopicFactory.createUserPrivateChannelTopic(userId)
    }
    
    /**
     * 시스템 알림 메시지를 모든 연결된 클라이언트에게 브로드캐스트합니다.
     * 
     * @param message 시스템 메시지
     * @param excludeUserId 제외할 사용자 ID (선택적)
     * @return 작업 완료 신호
     */
    fun broadcastSystemMessage(message: String, excludeUserId: Long? = null): Mono<Void> {
        val systemMessage = mapOf(
            "type" to "SYSTEM",
            "message" to message,
            "timestamp" to System.currentTimeMillis(),
            "excludeUserId" to excludeUserId
        )
        val channelTopic = channelTopicFactory.createGlobalChannelTopic()
        
        return publishMessage(channelTopic.topic, systemMessage)
            .then()
            .doOnSuccess {
                logger.info("System message broadcasted: {}", message)
            }
    }
}