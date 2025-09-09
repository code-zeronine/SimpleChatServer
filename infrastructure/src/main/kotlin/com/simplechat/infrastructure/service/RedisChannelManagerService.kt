package com.simplechat.infrastructure.service

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 통합된 Redis 채널 관리 서비스
 * 
 * 기존의 여러 채널 관리 서비스들을 하나로 통합:
 * - RedisChannelManager
 * - RedisChannelLifecycleService
 * - SubscriberTrackingService
 */
@Service
class RedisChannelManagerService(
    private val connectionFactory: ReactiveRedisConnectionFactory,
    private val messageListenerContainer: ReactiveRedisMessageListenerContainer
) {

    private val logger = LoggerFactory.getLogger(RedisChannelManagerService::class.java)
    
    // 채널 생명주기 관리
    private val channelLifecycle = ConcurrentHashMap<String, ChannelInfo>()
    
    // 구독자 추적
    private val subscriberTracking = ConcurrentHashMap<String, AtomicLong>()
    
    // 통계
    private val totalChannelsCreated = AtomicLong(0)
    private val totalChannelsDestroyed = AtomicLong(0)

    @PostConstruct
    fun initialize() {
        logger.info("Redis Channel Manager Service initialized")
        startPeriodicCleanup()
    }

    @PreDestroy
    fun cleanup() {
        channelLifecycle.clear()
        subscriberTracking.clear()
        logger.info("Redis Channel Manager Service cleaned up")
    }

    /**
     * 채널 생성 및 등록
     */
    fun createChannel(channelName: String): Mono<ChannelTopic> {
        return Mono.fromCallable {
            val topic = ChannelTopic.of(channelName)
            val channelInfo = ChannelInfo(
                name = channelName,
                createdAt = Instant.now(),
                lastUsed = Instant.now(),
                topic = topic
            )
            
            channelLifecycle[channelName] = channelInfo
            subscriberTracking[channelName] = AtomicLong(0)
            totalChannelsCreated.incrementAndGet()
            
            logger.debug("Channel created: {}", channelName)
            topic
        }
    }

    /**
     * 채널 제거
     */
    fun destroyChannel(channelName: String): Mono<Void> {
        return Mono.fromRunnable {
            channelLifecycle.remove(channelName)
            subscriberTracking.remove(channelName)
            totalChannelsDestroyed.incrementAndGet()
            
            logger.debug("Channel destroyed: {}", channelName)
        }
    }

    /**
     * 구독자 추가
     */
    fun addSubscriber(channelName: String): Mono<Long> {
        return Mono.fromCallable {
            // 채널 정보 업데이트
            channelLifecycle[channelName]?.let { info ->
                channelLifecycle[channelName] = info.copy(lastUsed = Instant.now())
            }
            
            // 구독자 수 증가
            val count = subscriberTracking.computeIfAbsent(channelName) { AtomicLong(0) }
                .incrementAndGet()
            
            logger.debug("Subscriber added to channel {}: {} total", channelName, count)
            count
        }
    }

    /**
     * 구독자 제거
     */
    fun removeSubscriber(channelName: String): Mono<Long> {
        return Mono.fromCallable {
            val count = subscriberTracking[channelName]?.decrementAndGet() ?: 0L
            
            if (count <= 0) {
                // 구독자가 없으면 채널을 정리 대상으로 표시
                channelLifecycle[channelName]?.let { info ->
                    channelLifecycle[channelName] = info.copy(shouldCleanup = true)
                }
            }
            
            logger.debug("Subscriber removed from channel {}: {} remaining", channelName, count)
            count
        }
    }

    /**
     * 채널 정보 조회
     */
    fun getChannelInfo(channelName: String): ChannelInfo? {
        return channelLifecycle[channelName]
    }

    /**
     * 모든 활성 채널 조회
     */
    fun getActiveChannels(): Map<String, ChannelInfo> {
        return channelLifecycle.toMap()
    }

    /**
     * 구독자 수 조회
     */
    fun getSubscriberCount(channelName: String): Long {
        return subscriberTracking[channelName]?.get() ?: 0L
    }

    /**
     * 채널 존재 여부 확인
     */
    fun channelExists(channelName: String): Boolean {
        return channelLifecycle.containsKey(channelName)
    }

    /**
     * 주기적 정리 작업 시작
     */
    private fun startPeriodicCleanup() {
        // 10분마다 사용되지 않는 채널 정리
        reactor.core.publisher.Flux.interval(Duration.ofMinutes(10))
            .flatMap { cleanupUnusedChannels() }
            .subscribe(
                { count -> 
                    if (count > 0) {
                        logger.info("Cleaned up {} unused channels", count)
                    }
                },
                { error -> logger.error("Error during channel cleanup", error) }
            )
    }

    /**
     * 사용되지 않는 채널 정리
     */
    fun cleanupUnusedChannels(): Mono<Int> {
        return Mono.fromCallable {
            val cutoff = Instant.now().minus(Duration.ofHours(1))
            var cleanedCount = 0
            
            val channelsToRemove = mutableListOf<String>()
            
            channelLifecycle.forEach { (channelName, info) ->
                val subscriberCount = subscriberTracking[channelName]?.get() ?: 0L
                
                if (subscriberCount == 0L && 
                    (info.shouldCleanup || info.lastUsed.isBefore(cutoff))) {
                    channelsToRemove.add(channelName)
                }
            }
            
            channelsToRemove.forEach { channelName ->
                channelLifecycle.remove(channelName)
                subscriberTracking.remove(channelName)
                totalChannelsDestroyed.incrementAndGet()
                cleanedCount++
                logger.debug("Cleaned up unused channel: {}", channelName)
            }
            
            cleanedCount
        }
    }

    /**
     * 통계 정보 조회
     */
    fun getStatistics(): Map<String, Any> {
        return mapOf(
            "totalChannelsCreated" to totalChannelsCreated.get(),
            "totalChannelsDestroyed" to totalChannelsDestroyed.get(),
            "activeChannels" to channelLifecycle.size,
            "totalSubscribers" to subscriberTracking.values.sumOf { it.get() },
            "averageSubscribersPerChannel" to if (channelLifecycle.isEmpty()) 0.0 
                else subscriberTracking.values.sumOf { it.get() }.toDouble() / channelLifecycle.size
        )
    }

    /**
     * 헬스 체크
     */
    fun healthCheck(): Mono<Map<String, Any>> {
        return Mono.just(
            mapOf(
                "status" to "UP",
                "activeChannels" to channelLifecycle.size,
                "totalSubscribers" to subscriberTracking.values.sumOf { it.get() },
                "totalCreated" to totalChannelsCreated.get(),
                "totalDestroyed" to totalChannelsDestroyed.get()
            )
        )
    }
}
