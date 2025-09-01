package com.simplechat.infrastructure.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.simplechat.dto.WebSocketMessage
import com.simplechat.service.MessageBroker
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class RedisMessageBroker(
    private val redisTemplate: ReactiveRedisTemplate<String, Any>,
    private val listenerContainer: ReactiveRedisMessageListenerContainer,
    private val sessionManager: WebSocketSessionManager,
    private val objectMapper: ObjectMapper
) : MessageBroker {

    private val log = LoggerFactory.getLogger(javaClass)
    private val topicPrefix = "chat.room."

    @PostConstruct
    fun subscribeToTopics() {
        listenerContainer.receive(ChannelTopic.of("$topicPrefix*"))
            .flatMap { message ->
                try {
                    val chatRoomId = message.channel.removePrefix(topicPrefix)
                    val wsMessage = objectMapper.readValue(message.message, WebSocketMessage::class.java)
                    val sessions = sessionManager.getSessionsByChatRoom(chatRoomId)
                    
                    val sendMonos = sessions.map { session ->
                        session.send(Mono.just(session.textMessage(objectMapper.writeValueAsString(wsMessage))))
                            .doOnError { error -> log.error("Failed to send message to session {}", session.id, error) }
                    }
                    Mono.`when`(sendMonos)
                } catch (e: Exception) {
                    log.error("Error processing message from Redis: {}", message, e)
                    Mono.empty<Void>()
                }
            }.subscribe()
        log.info("Subscribed to Redis topic: {}*", topicPrefix)
    }

    override fun broadcast(chatRoomId: String, message: WebSocketMessage) {
        redisTemplate.convertAndSend("$topicPrefix$chatRoomId", message)
            .doOnSuccess { log.debug("Message broadcasted to Redis topic: {}{}", topicPrefix, chatRoomId) }
            .doOnError { log.error("Failed to broadcast message to Redis", it) }
            .subscribe()
    }
}
