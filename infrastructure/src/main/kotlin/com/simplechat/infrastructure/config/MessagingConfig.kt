package com.simplechat.infrastructure.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.simplechat.domain.constants.RedisChannelConstants
import com.simplechat.infrastructure.handler.ChatWebSocketHandler
import com.simplechat.infrastructure.security.WebSocketAuthService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import org.springframework.stereotype.Component
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.reactive.socket.server.upgrade.ReactorNettyRequestUpgradeStrategy

/**
 * 통합 메시징 설정
 * 
 * 실시간 메시징과 관련된 모든 설정을 통합 관리합니다:
 * - Redis Pub/Sub 설정 및 직렬화
 * - WebSocket 핸들러 및 매핑
 * - 채널 토픽 팩토리
 */
@Configuration
class MessagingConfig {

    private val logger = LoggerFactory.getLogger(MessagingConfig::class.java)

    // ===========================================
    // Redis 설정
    // ===========================================

    @Bean
    fun pubSubMessageListenerContainer(connectionFactory: ReactiveRedisConnectionFactory): ReactiveRedisMessageListenerContainer {
        return ReactiveRedisMessageListenerContainer(connectionFactory)
    }

    @Bean
    fun reactiveRedisTemplate(factory: ReactiveRedisConnectionFactory): ReactiveRedisTemplate<String, Any> {
        val objectMapper = ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .registerModule(JavaTimeModule())

        val stringSerializer = StringRedisSerializer.UTF_8
        val jackson2JsonRedisSerializer = Jackson2JsonRedisSerializer(objectMapper, Any::class.java)

        val serializationContext = RedisSerializationContext.newSerializationContext<String, Any>()
            .key(stringSerializer)
            .value(jackson2JsonRedisSerializer)
            .hashKey(stringSerializer)
            .hashValue(jackson2JsonRedisSerializer)
            .build()
        return ReactiveRedisTemplate(factory, serializationContext)
    }

    @Bean
    fun reactiveStringRedisTemplate(factory: ReactiveRedisConnectionFactory): ReactiveRedisTemplate<String, String> {
        val stringSerializer = StringRedisSerializer.UTF_8
        val serializationContext = RedisSerializationContext.newSerializationContext<String, String>()
            .key(stringSerializer)
            .value(stringSerializer)
            .hashKey(stringSerializer)
            .hashValue(stringSerializer)
            .build()
        return ReactiveRedisTemplate(factory, serializationContext)
    }

    // ===========================================
    // WebSocket 설정
    // ===========================================

    @Bean
    fun webSocketHandlerAdapter(webSocketAuthService: WebSocketAuthService): WebSocketHandlerAdapter {
        return WebSocketHandlerAdapter(webSocketAuthService)
    }

    @Bean
    fun webSocketHandlerMapping(chatWebSocketHandler: ChatWebSocketHandler): HandlerMapping {
        val map = mapOf<String, WebSocketHandler>(
            "/ws/chat/**" to chatWebSocketHandler
        )
        
        val handlerMapping = SimpleUrlHandlerMapping()
        handlerMapping.urlMap = map
        handlerMapping.order = 1
        
        // WebSocket 전용 CORS 설정
        val corsConfig = CorsConfiguration().apply {
            allowedOriginPatterns = listOf("*")
            allowedMethods = listOf("GET", "POST", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
            maxAge = 3600L // 1시간
        }
        handlerMapping.setCorsConfigurations(mapOf("/ws/**" to corsConfig))
        
        return handlerMapping
    }

    // ===========================================
    // 채널 토픽 팩토리 (인라인 구현)
    // ===========================================

    @Bean
    fun channelTopicFactory(): ChannelTopicFactory {
        return ChannelTopicFactory()
    }

    @Component
    class ChannelTopicFactory {
        
        private val logger = LoggerFactory.getLogger(ChannelTopicFactory::class.java)
        
        fun createRoomChannelTopic(roomId: Long): ChannelTopic {
            val channelName = RedisChannelConstants.createRoomChannelName(roomId)
            logger.debug("Creating room channel topic: {}", channelName)
            return ChannelTopic.of(channelName)
        }
        
        fun createUserPrivateChannelTopic(userId: Long): ChannelTopic {
            val channelName = RedisChannelConstants.createUserPrivateChannelName(userId)
            logger.debug("Creating user private channel topic: {}", channelName)
            return ChannelTopic.of(channelName)
        }
        
        fun createGlobalChannelTopic(): ChannelTopic {
            logger.debug("Creating global channel topic: {}", RedisChannelConstants.GLOBAL_CHANNEL)
            return ChannelTopic.of(RedisChannelConstants.GLOBAL_CHANNEL)
        }
        
        fun createSystemChannelTopic(): ChannelTopic {
            logger.debug("Creating system channel topic: {}", RedisChannelConstants.SYSTEM_CHANNEL)
            return ChannelTopic.of(RedisChannelConstants.SYSTEM_CHANNEL)
        }
        
        fun createAdminChannelTopic(): ChannelTopic {
            logger.debug("Creating admin channel topic: {}", RedisChannelConstants.ADMIN_CHANNEL)
            return ChannelTopic.of(RedisChannelConstants.ADMIN_CHANNEL)
        }
        
        fun createCustomChannelTopic(channelName: String): ChannelTopic {
            require(RedisChannelConstants.isValidChannelName(channelName)) {
                "Invalid channel name: $channelName"
            }
            logger.debug("Creating custom channel topic: {}", channelName)
            return ChannelTopic.of(channelName)
        }
        
        fun createMultipleRoomChannelTopics(roomIds: List<Long>): List<ChannelTopic> {
            return roomIds.map { roomId ->
                createRoomChannelTopic(roomId)
            }.also { topics ->
                logger.debug("Created {} room channel topics", topics.size)
            }
        }
    }
}