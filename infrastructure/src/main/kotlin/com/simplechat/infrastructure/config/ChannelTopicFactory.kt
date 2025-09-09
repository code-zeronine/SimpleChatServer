package com.simplechat.infrastructure.config

import com.simplechat.domain.constants.RedisChannelConstants
import org.slf4j.LoggerFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.stereotype.Component

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
