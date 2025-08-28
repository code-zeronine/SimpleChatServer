package com.simplechat.infrastructure.config

import com.simplechat.domain.constants.RedisChannelConstants
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.data.redis.listener.ChannelTopic

/**
 * 채널 토픽을 동적으로 생성하기 위한 팩토리 클래스
 * 
 * 단순한 ChannelTopic 생성에만 집중하며, 도메인 상수를 사용합니다.
 * 메타데이터 관리나 복잡한 비즈니스 로직은 RedisChannelManager에서 담당합니다.
 */
@Component
class ChannelTopicFactory {
    
    private val logger = LoggerFactory.getLogger(ChannelTopicFactory::class.java)
    
    /**
     * 채팅방 채널 토픽을 생성합니다.
     *
     * @param roomId 채팅방 ID
     * @return 채팅방 ChannelTopic
     */
    fun createRoomChannelTopic(roomId: Long): ChannelTopic {
        val channelName = RedisChannelConstants.createRoomChannelName(roomId)
        logger.debug("Creating room channel topic: {}", channelName)
        return ChannelTopic.of(channelName)
    }
    
    /**
     * 사용자 개인 채널 토픽을 생성합니다.
     *
     * @param userId 사용자 ID
     * @return 사용자 ChannelTopic
     */
    fun createUserPrivateChannelTopic(userId: Long): ChannelTopic {
        val channelName = RedisChannelConstants.createUserPrivateChannelName(userId)
        logger.debug("Creating user private channel topic: {}", channelName)
        return ChannelTopic.of(channelName)
    }
    
    /**
     * 글로벌 채널 토픽을 생성합니다.
     *
     * @return 글로벌 ChannelTopic
     */
    fun createGlobalChannelTopic(): ChannelTopic {
        logger.debug("Creating global channel topic: {}", RedisChannelConstants.GLOBAL_CHANNEL)
        return ChannelTopic.of(RedisChannelConstants.GLOBAL_CHANNEL)
    }
    
    /**
     * 시스템 채널 토픽을 생성합니다.
     *
     * @return 시스템 ChannelTopic
     */
    fun createSystemChannelTopic(): ChannelTopic {
        logger.debug("Creating system channel topic: {}", RedisChannelConstants.SYSTEM_CHANNEL)
        return ChannelTopic.of(RedisChannelConstants.SYSTEM_CHANNEL)
    }
    
    /**
     * 관리자 채널 토픽을 생성합니다.
     *
     * @return 관리자 ChannelTopic
     */
    fun createAdminChannelTopic(): ChannelTopic {
        logger.debug("Creating admin channel topic: {}", RedisChannelConstants.ADMIN_CHANNEL)
        return ChannelTopic.of(RedisChannelConstants.ADMIN_CHANNEL)
    }
    
    /**
     * 커스텀 채널 토픽을 생성합니다.
     *
     * @param channelName 채널명
     * @return 커스텀 ChannelTopic
     */
    fun createCustomChannelTopic(channelName: String): ChannelTopic {
        require(RedisChannelConstants.isValidChannelName(channelName)) {
            "Invalid channel name: $channelName"
        }
        logger.debug("Creating custom channel topic: {}", channelName)
        return ChannelTopic.of(channelName)
    }
    
    /**
     * 여러 채팅방의 채널 토픽을 생성합니다.
     *
     * @param roomIds 채팅방 ID 목록
     * @return ChannelTopic 목록
     */
    fun createMultipleRoomChannelTopics(roomIds: List<Long>): List<ChannelTopic> {
        return roomIds.map { roomId ->
            createRoomChannelTopic(roomId)
        }.also { topics ->
            logger.debug("Created {} room channel topics", topics.size)
        }
    }
}