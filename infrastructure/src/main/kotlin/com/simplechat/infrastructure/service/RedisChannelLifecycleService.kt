package com.simplechat.infrastructure.service

import com.simplechat.domain.constants.RedisChannelConstants
import com.simplechat.domain.constants.ChannelType
import org.slf4j.LoggerFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy

/**
 * Redis 채널 생명주기 관리 서비스
 * 
 * 채널의 동적 생성, 삭제, 정리를 관리하고 채널 상태를 추적합니다.
 * 자동 정리 및 최적화 기능을 제공합니다.
 */
@Service
class RedisChannelLifecycleService(
    private val channelManager: RedisChannelManager,
    private val redisPubSubService: RedisPubSubService
) {
    
    private val logger = LoggerFactory.getLogger(RedisChannelLifecycleService::class.java)
    
    // 활성 채널 추적
    private val activeChannels = ConcurrentHashMap<String, ChannelLifecycleInfo>()
    
    // 채널 이벤트 스트림
    private val channelEventSink = Sinks.many().multicast().onBackpressureBuffer<ChannelLifecycleEvent>()
    
    // 통계
    private val channelCreatedCount = AtomicLong(0)
    private val channelDestroyedCount = AtomicLong(0)
    private val channelCleanupCount = AtomicLong(0)
    
    companion object {
        // 채널 정리 기준 시간 (밀리초)
        private const val INACTIVE_CHANNEL_THRESHOLD = 30 * 60 * 1000L // 30분
        private const val CLEANUP_INTERVAL = 5 * 60 * 1000L // 5분
        private const val MAX_IDLE_CHANNELS = 1000
    }
    
    @PostConstruct
    fun init() {
        logger.info("Redis channel lifecycle service initialized")
        
        // 시스템 채널들을 미리 생성
        initializeSystemChannels()
    }
    
    @PreDestroy
    fun destroy() {
        // 모든 활성 채널 정리
        cleanupAllChannels()
        
        // 이벤트 스트림 종료
        channelEventSink.tryEmitComplete()
        
        logger.info("Redis channel lifecycle service destroyed")
    }
    
    /**
     * 채팅방 채널을 생성하고 활성화합니다.
     * 
     * @param roomId 채팅방 ID
     * @return 생성된 ChannelTopic
     */
    fun createRoomChannel(roomId: Long): Mono<ChannelTopic> {
        return channelManager.createManagedRoomChannel(roomId)
            .doOnSuccess { channelTopic ->
                val channelName = channelTopic.topic
                val lifecycleInfo = ChannelLifecycleInfo(
                    channelName = channelName,
                    channelType = ChannelType.CHAT_ROOM,
                    createdAt = System.currentTimeMillis(),
                    lastActivityAt = System.currentTimeMillis(),
                    isActive = true,
                    roomId = roomId
                )
                
                activeChannels[channelName] = lifecycleInfo
                channelCreatedCount.incrementAndGet()
                
                // 채널 생성 이벤트 발행
                emitChannelEvent(ChannelLifecycleEvent(
                    type = ChannelEventType.CREATED,
                    channelName = channelName,
                    channelType = ChannelType.CHAT_ROOM,
                    roomId = roomId,
                    timestamp = System.currentTimeMillis()
                ))
                
                logger.debug("Room channel created: {} for room {}", channelName, roomId)
            }
    }
    
    /**
     * 사용자 개인 채널을 생성하고 활성화합니다.
     * 
     * @param userId 사용자 ID
     * @return 생성된 ChannelTopic
     */
    fun createUserPrivateChannel(userId: Long): Mono<ChannelTopic> {
        return channelManager.createManagedUserPrivateChannel(userId)
            .doOnSuccess { channelTopic ->
                val channelName = channelTopic.topic
                val lifecycleInfo = ChannelLifecycleInfo(
                    channelName = channelName,
                    channelType = ChannelType.USER_PRIVATE,
                    createdAt = System.currentTimeMillis(),
                    lastActivityAt = System.currentTimeMillis(),
                    isActive = true,
                    userId = userId
                )
                
                activeChannels[channelName] = lifecycleInfo
                channelCreatedCount.incrementAndGet()
                
                // 채널 생성 이벤트 발행
                emitChannelEvent(ChannelLifecycleEvent(
                    type = ChannelEventType.CREATED,
                    channelName = channelName,
                    channelType = ChannelType.USER_PRIVATE,
                    userId = userId,
                    timestamp = System.currentTimeMillis()
                ))
                
                logger.debug("User private channel created: {} for user {}", channelName, userId)
            }
    }
    
    /**
     * 채널을 비활성화합니다.
     * 
     * @param channelName 채널명
     * @return 비활성화 성공 여부
     */
    fun deactivateChannel(channelName: String): Mono<Boolean> {
        return Mono.fromCallable {
            activeChannels[channelName]?.let { info ->
                val updatedInfo = info.copy(
                    isActive = false,
                    deactivatedAt = System.currentTimeMillis()
                )
                activeChannels[channelName] = updatedInfo
                
                // 채널 관리자에도 반영
                channelManager.deactivateChannel(channelName)
                
                // 비활성화 이벤트 발행
                emitChannelEvent(ChannelLifecycleEvent(
                    type = ChannelEventType.DEACTIVATED,
                    channelName = channelName,
                    channelType = info.channelType,
                    roomId = info.roomId,
                    userId = info.userId,
                    timestamp = System.currentTimeMillis()
                ))
                
                logger.debug("Channel deactivated: {}", channelName)
                true
            } ?: false
        }
    }
    
    /**
     * 채널을 완전히 제거합니다.
     * 
     * @param channelName 채널명
     * @return 제거 성공 여부
     */
    fun destroyChannel(channelName: String): Mono<Boolean> {
        return Mono.fromCallable {
            activeChannels.remove(channelName)?.let { info ->
                channelManager.removeChannel(channelName)
                channelDestroyedCount.incrementAndGet()
                
                // 채널 제거 이벤트 발행
                emitChannelEvent(ChannelLifecycleEvent(
                    type = ChannelEventType.DESTROYED,
                    channelName = channelName,
                    channelType = info.channelType,
                    roomId = info.roomId,
                    userId = info.userId,
                    timestamp = System.currentTimeMillis()
                ))
                
                logger.debug("Channel destroyed: {}", channelName)
                true
            } ?: false
        }
    }
    
    /**
     * 채널 활동을 업데이트합니다.
     * 
     * @param channelName 채널명
     * @return 업데이트 성공 여부
     */
    fun updateChannelActivity(channelName: String): Mono<Boolean> {
        return Mono.fromCallable {
            activeChannels[channelName]?.let { info ->
                val updatedInfo = info.copy(
                    lastActivityAt = System.currentTimeMillis()
                )
                activeChannels[channelName] = updatedInfo
                
                logger.trace("Channel activity updated: {}", channelName)
                true
            } ?: false
        }
    }
    
    /**
     * 채널별 구독자 수를 업데이트합니다.
     * 
     * @param channelName 채널명
     * @param subscriberCount 구독자 수
     * @return 업데이트 성공 여부
     */
    fun updateSubscriberCount(channelName: String, subscriberCount: Int): Mono<Boolean> {
        return Mono.fromCallable {
            activeChannels[channelName]?.let { info ->
                val updatedInfo = info.copy(
                    subscriberCount = subscriberCount,
                    lastActivityAt = System.currentTimeMillis()
                )
                activeChannels[channelName] = updatedInfo
                
                logger.trace("Subscriber count updated for channel {}: {}", channelName, subscriberCount)
                true
            } ?: false
        }
    }
    
    /**
     * 활성 채널 목록을 조회합니다.
     * 
     * @return 활성 채널 정보 맵
     */
    fun getActiveChannels(): Map<String, ChannelLifecycleInfo> {
        return activeChannels.toMap()
    }
    
    /**
     * 특정 타입의 활성 채널들을 조회합니다.
     * 
     * @param channelType 채널 타입
     * @return 해당 타입의 채널 정보 목록
     */
    fun getActiveChannelsByType(channelType: ChannelType): List<ChannelLifecycleInfo> {
        return activeChannels.values
            .filter { it.channelType == channelType && it.isActive }
            .toList()
    }
    
    /**
     * 채널 생명주기 이벤트 스트림을 구독합니다.
     * 
     * @return 채널 이벤트 스트림
     */
    fun subscribeToChannelEvents(): Flux<ChannelLifecycleEvent> {
        return channelEventSink.asFlux()
            .doOnSubscribe { 
                logger.debug("New subscriber to channel lifecycle events")
            }
    }
    
    /**
     * 채널 통계 정보를 조회합니다.
     * 
     * @return 통계 정보
     */
    fun getChannelStatistics(): Mono<ChannelStatistics> {
        return Mono.fromCallable {
            val activeCount = activeChannels.count { it.value.isActive }
            val inactiveCount = activeChannels.size - activeCount
            
            val typeStatistics = activeChannels.values
                .groupBy { it.channelType }
                .mapValues { entry -> entry.value.size }
            
            ChannelStatistics(
                totalChannels = activeChannels.size,
                activeChannels = activeCount,
                inactiveChannels = inactiveCount,
                channelsCreated = channelCreatedCount.get(),
                channelsDestroyed = channelDestroyedCount.get(),
                channelsCleanedUp = channelCleanupCount.get(),
                channelsByType = typeStatistics,
                timestamp = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * 비활성 채널들을 정리합니다. (스케줄링)
     */
    @Scheduled(fixedDelay = CLEANUP_INTERVAL)
    fun cleanupInactiveChannels() {
        val currentTime = System.currentTimeMillis()
        val channelsToRemove = mutableListOf<String>()
        
        activeChannels.forEach { (channelName, info) ->
            val shouldCleanup = when {
                // 비활성 상태이고 임계시간 경과
                !info.isActive && info.deactivatedAt != null && 
                (currentTime - info.deactivatedAt) > INACTIVE_CHANNEL_THRESHOLD -> true
                
                // 활성 상태이지만 오랫동안 활동 없음
                info.isActive && (currentTime - info.lastActivityAt) > (INACTIVE_CHANNEL_THRESHOLD * 2) -> true
                
                // 구독자가 없는 상태로 오래 유지됨
                info.subscriberCount == 0 && (currentTime - info.lastActivityAt) > INACTIVE_CHANNEL_THRESHOLD -> true
                
                else -> false
            }
            
            if (shouldCleanup && !isSystemChannel(channelName)) {
                channelsToRemove.add(channelName)
            }
        }
        
        // 최대 idle 채널 수 제한
        if (activeChannels.size > MAX_IDLE_CHANNELS) {
            val oldestChannels = activeChannels.values
                .filter { !it.isActive && !isSystemChannel(it.channelName) }
                .sortedBy { it.lastActivityAt }
                .take(activeChannels.size - MAX_IDLE_CHANNELS)
                .map { it.channelName }
            
            channelsToRemove.addAll(oldestChannels)
        }
        
        // 정리 실행
        channelsToRemove.forEach { channelName ->
            destroyChannel(channelName).subscribe()
            channelCleanupCount.incrementAndGet()
        }
        
        if (channelsToRemove.isNotEmpty()) {
            logger.info("Cleaned up {} inactive channels", channelsToRemove.size)
        }
    }
    
    /**
     * 시스템 채널들을 초기화합니다.
     */
    private fun initializeSystemChannels() {
        val systemChannels = listOf(
            RedisChannelConstants.GLOBAL_CHANNEL to ChannelType.GLOBAL,
            RedisChannelConstants.SYSTEM_CHANNEL to ChannelType.SYSTEM,
            RedisChannelConstants.ADMIN_CHANNEL to ChannelType.ADMIN
        )
        
        systemChannels.forEach { (channelName, channelType) ->
            val lifecycleInfo = ChannelLifecycleInfo(
                channelName = channelName,
                channelType = channelType,
                createdAt = System.currentTimeMillis(),
                lastActivityAt = System.currentTimeMillis(),
                isActive = true,
                isPersistent = true
            )
            
            activeChannels[channelName] = lifecycleInfo
            logger.debug("System channel initialized: {}", channelName)
        }
    }
    
    /**
     * 시스템 채널인지 확인합니다.
     */
    private fun isSystemChannel(channelName: String): Boolean {
        return channelName in listOf(
            RedisChannelConstants.GLOBAL_CHANNEL,
            RedisChannelConstants.SYSTEM_CHANNEL,
            RedisChannelConstants.ADMIN_CHANNEL
        )
    }
    
    /**
     * 모든 채널을 정리합니다.
     */
    private fun cleanupAllChannels() {
        val nonPersistentChannels = activeChannels.filter { !it.value.isPersistent }
        
        nonPersistentChannels.forEach { (channelName, _) ->
            destroyChannel(channelName).subscribe()
        }
        
        logger.info("Cleaned up {} channels during shutdown", nonPersistentChannels.size)
    }
    
    /**
     * 채널 이벤트를 발행합니다.
     */
    private fun emitChannelEvent(event: ChannelLifecycleEvent) {
        val result = channelEventSink.tryEmitNext(event)
        if (result.isFailure) {
            logger.warn("Failed to emit channel event: {} for channel {}", 
                result, event.channelName)
        }
    }
}

/**
 * 채널 생명주기 정보 데이터 클래스
 */
data class ChannelLifecycleInfo(
    val channelName: String,
    val channelType: ChannelType,
    val createdAt: Long,
    val lastActivityAt: Long,
    val isActive: Boolean,
    val deactivatedAt: Long? = null,
    val roomId: Long? = null,
    val userId: Long? = null,
    val subscriberCount: Int = 0,
    val isPersistent: Boolean = false
)

/**
 * 채널 생명주기 이벤트 데이터 클래스
 */
data class ChannelLifecycleEvent(
    val type: ChannelEventType,
    val channelName: String,
    val channelType: ChannelType,
    val roomId: Long? = null,
    val userId: Long? = null,
    val subscriberCount: Int? = null,
    val timestamp: Long
)

/**
 * 채널 이벤트 타입 열거형
 */
enum class ChannelEventType {
    CREATED,        // 채널 생성
    ACTIVATED,      // 채널 활성화
    DEACTIVATED,    // 채널 비활성화
    DESTROYED,      // 채널 제거
    SUBSCRIBER_CHANGED  // 구독자 수 변경
}

/**
 * 채널 통계 정보 데이터 클래스
 */
data class ChannelStatistics(
    val totalChannels: Int,
    val activeChannels: Int,
    val inactiveChannels: Int,
    val channelsCreated: Long,
    val channelsDestroyed: Long,
    val channelsCleanedUp: Long,
    val channelsByType: Map<ChannelType, Int>,
    val timestamp: Long
)