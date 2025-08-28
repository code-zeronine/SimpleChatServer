package com.simplechat.infrastructure.service

import com.simplechat.domain.constants.RedisChannelConstants
import com.simplechat.domain.constants.ChannelType
import com.simplechat.infrastructure.config.ChannelTopicFactory
import org.slf4j.LoggerFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

/**
 * Redis 채널 관리 서비스
 * 
 * 채팅방별 Redis 채널의 메타데이터 관리, 생명주기 관리를 담당합니다.
 * 단순한 ChannelTopic 생성은 ChannelTopicFactory를 사용합니다.
 */
@Service
class RedisChannelManager(
    private val channelTopicFactory: ChannelTopicFactory
) {
    
    private val logger = LoggerFactory.getLogger(RedisChannelManager::class.java)
    
    // 채널별 메타데이터 저장소
    private val channelMetadata = ConcurrentHashMap<String, ChannelMetadata>()
    
    
    
    /**
     * 채팅방 ChannelTopic을 생성하고 메타데이터를 등록합니다.
     * 
     * @param roomId 채팅방 ID
     * @return ChannelTopic과 메타데이터가 등록된 결과
     */
    fun createManagedRoomChannel(roomId: Long): Mono<ChannelTopic> {
        return Mono.fromCallable {
            val channelTopic = channelTopicFactory.createRoomChannelTopic(roomId)
            
            // 메타데이터 등록
            val metadata = ChannelMetadata(
                channelName = channelTopic.topic,
                channelType = ChannelType.CHAT_ROOM,
                roomId = roomId,
                createdAt = System.currentTimeMillis(),
                isActive = true
            )
            
            channelMetadata[channelTopic.topic] = metadata
            logger.debug("Created and registered room channel topic: {}", channelTopic.topic)
            
            channelTopic
        }
    }
    
    /**
     * 사용자 개인 ChannelTopic을 생성하고 메타데이터를 등록합니다.
     * 
     * @param userId 사용자 ID
     * @return ChannelTopic과 메타데이터가 등록된 결과
     */
    fun createManagedUserPrivateChannel(userId: Long): Mono<ChannelTopic> {
        return Mono.fromCallable {
            val channelTopic = channelTopicFactory.createUserPrivateChannelTopic(userId)
            
            // 메타데이터 등록
            val metadata = ChannelMetadata(
                channelName = channelTopic.topic,
                channelType = ChannelType.USER_PRIVATE,
                userId = userId,
                createdAt = System.currentTimeMillis(),
                isActive = true
            )
            
            channelMetadata[channelTopic.topic] = metadata
            logger.debug("Created and registered user private channel topic: {}", channelTopic.topic)
            
            channelTopic
        }
    }
    
    /**
     * 채널명이 유효한지 검증합니다.
     * 
     * @param channelName 검증할 채널명
     * @return 검증 결과
     */
    fun validateChannelName(channelName: String): Mono<Boolean> {
        return Mono.fromCallable {
            val isValid = RedisChannelConstants.isValidChannelName(channelName)
            if (isValid) {
                logger.trace("Channel name validation passed: {}", channelName)
            } else {
                logger.warn("Channel name validation failed: {}", channelName)
            }
            isValid
        }
    }
    
    
    
    /**
     * 채널 메타데이터를 조회합니다.
     * 
     * @param channelName 채널명
     * @return 메타데이터 (존재하지 않으면 null)
     */
    fun getChannelMetadata(channelName: String): ChannelMetadata? {
        return channelMetadata[channelName]
    }
    
    /**
     * 모든 등록된 채널 메타데이터를 조회합니다.
     * 
     * @return 채널 메타데이터 맵
     */
    fun getAllChannelMetadata(): Map<String, ChannelMetadata> {
        return channelMetadata.toMap()
    }
    
    /**
     * 특정 타입의 채널들을 조회합니다.
     * 
     * @param channelType 채널 타입
     * @return 해당 타입의 채널 메타데이터 목록
     */
    fun getChannelsByType(channelType: ChannelType): List<ChannelMetadata> {
        return channelMetadata.values
            .filter { it.channelType == channelType }
            .toList()
    }
    
    /**
     * 채널을 비활성화합니다.
     * 
     * @param channelName 채널명
     * @return 비활성화 성공 여부
     */
    fun deactivateChannel(channelName: String): Boolean {
        return channelMetadata[channelName]?.let { metadata ->
            channelMetadata[channelName] = metadata.copy(
                isActive = false,
                deactivatedAt = System.currentTimeMillis()
            )
            logger.debug("Deactivated channel: {}", channelName)
            true
        } ?: false
    }
    
    /**
     * 채널을 활성화합니다.
     * 
     * @param channelName 채널명
     * @return 활성화 성공 여부
     */
    fun activateChannel(channelName: String): Boolean {
        return channelMetadata[channelName]?.let { metadata ->
            channelMetadata[channelName] = metadata.copy(
                isActive = true,
                deactivatedAt = null
            )
            logger.debug("Activated channel: {}", channelName)
            true
        } ?: false
    }
    
    /**
     * 채널 메타데이터를 제거합니다.
     * 
     * @param channelName 채널명
     * @return 제거 성공 여부
     */
    fun removeChannel(channelName: String): Boolean {
        val removed = channelMetadata.remove(channelName) != null
        if (removed) {
            logger.debug("Removed channel metadata: {}", channelName)
        }
        return removed
    }
    
}

/**
 * 채널 메타데이터 데이터 클래스
 */
data class ChannelMetadata(
    val channelName: String,
    val channelType: ChannelType,
    val roomId: Long? = null,
    val userId: Long? = null,
    val createdAt: Long,
    val isActive: Boolean,
    val deactivatedAt: Long? = null,
    val lastActivityAt: Long = System.currentTimeMillis(),
    val subscriberCount: Int = 0,
    val messageCount: Long = 0L
)