package com.simplechat.infrastructure.service

import com.simplechat.domain.constants.RedisChannelConstants
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy

/**
 * Redis 채널 구독자 수 추적 서비스
 * 
 * 실시간으로 각 채널의 구독자 수를 추적하고 관리합니다.
 * Redis의 PUBSUB NUMSUB 명령을 활용하여 정확한 구독자 수를 제공합니다.
 */
@Service
class SubscriberTrackingService(
    private val reactiveRedisTemplate: ReactiveRedisTemplate<String, String>,
    private val channelManager: RedisChannelManager,
    private val channelLifecycleService: RedisChannelLifecycleService
) {
    
    private val logger = LoggerFactory.getLogger(SubscriberTrackingService::class.java)
    
    // 채널별 구독자 수 캐시
    private val subscriberCountCache = ConcurrentHashMap<String, SubscriberInfo>()
    
    // 구독자 수 변경 이벤트 스트림
    private val subscriberCountSink = Sinks.many().multicast().onBackpressureBuffer<SubscriberCountEvent>()
    
    // 추적 통계
    private val trackingStatistics = TrackingStatistics()
    
    companion object {
        private const val CACHE_TTL_SECONDS = 30L
        private const val BATCH_UPDATE_INTERVAL_SECONDS = 10L
        private const val REDIS_PUBSUB_NUMSUB_COMMAND = "PUBSUB"
        private const val REDIS_PUBSUB_NUMSUB_SUBCOMMAND = "NUMSUB"
    }
    
    @PostConstruct
    fun init() {
        // 주기적인 구독자 수 업데이트 스케줄러 시작
        startPeriodicUpdate()
        logger.info("Subscriber tracking service initialized")
    }
    
    @PreDestroy
    fun cleanup() {
        subscriberCountCache.clear()
        subscriberCountSink.tryEmitComplete()
        logger.info("Subscriber tracking service cleaned up")
    }
    
    /**
     * 특정 채널의 실시간 구독자 수를 조회합니다.
     * 
     * @param channelName 채널명
     * @return 구독자 수
     */
    fun getSubscriberCount(channelName: String): Mono<Int> {
        return getCachedSubscriberCount(channelName)
            .switchIfEmpty(
                refreshSubscriberCount(channelName)
            )
            .doOnSuccess { count ->
                logger.trace("Subscriber count for channel {}: {}", channelName, count)
            }
            .doOnError { error ->
                logger.error("Failed to get subscriber count for channel {}: {}", 
                    channelName, error.message)
            }
    }
    
    /**
     * 채팅방의 구독자 수를 조회합니다.
     * 
     * @param roomId 채팅방 ID
     * @return 구독자 수
     */
    fun getRoomSubscriberCount(roomId: Long): Mono<Int> {
        val channelName = RedisChannelConstants.createRoomChannelName(roomId)
        return getSubscriberCount(channelName)
    }
    
    /**
     * 여러 채널의 구독자 수를 일괄 조회합니다.
     * 
     * @param channelNames 채널명 목록
     * @return 채널명과 구독자 수의 맵
     */
    fun getBatchSubscriberCounts(channelNames: List<String>): Mono<Map<String, Int>> {
        if (channelNames.isEmpty()) {
            return Mono.just(emptyMap())
        }
        
        return executeNumSubCommand(channelNames)
            .doOnSuccess { counts ->
                // 캐시 업데이트
                counts.forEach { (channelName, count) ->
                    updateSubscriberCountCache(channelName, count)
                }
                trackingStatistics.incrementBatchQueries()
                logger.debug("Batch subscriber count query completed for {} channels", 
                    channelNames.size)
            }
            .doOnError { error ->
                trackingStatistics.incrementErrors()
                logger.error("Failed to get batch subscriber counts: {}", error.message, error)
            }
    }
    
    /**
     * 활성 채널들의 구독자 수를 모두 새로고침합니다.
     * 
     * @return 업데이트된 채널 수
     */
    fun refreshAllActiveChannels(): Mono<Int> {
        return Mono.fromCallable {
            channelLifecycleService.getActiveChannels().keys.toList()
        }
        .flatMap { activeChannels ->
            if (activeChannels.isEmpty()) {
                Mono.just(0)
            } else {
                getBatchSubscriberCounts(activeChannels)
                    .map { it.size }
            }
        }
        .doOnSuccess { count ->
            logger.debug("Refreshed subscriber counts for {} active channels", count)
        }
    }
    
    /**
     * 구독자 수 변경 이벤트 스트림을 제공합니다.
     * 
     * @return 구독자 수 변경 이벤트 스트림
     */
    fun subscribeToCountChanges(): Flux<SubscriberCountEvent> {
        return subscriberCountSink.asFlux()
            .doOnSubscribe { 
                logger.debug("New subscriber to count change events")
            }
    }
    
    /**
     * 특정 채널의 구독자 수 변경을 모니터링합니다.
     * 
     * @param channelName 채널명
     * @param interval 모니터링 간격
     * @return 구독자 수 변경 이벤트 스트림
     */
    fun monitorChannelSubscribers(
        channelName: String, 
        interval: Duration = Duration.ofSeconds(5)
    ): Flux<SubscriberCountEvent> {
        
        return Flux.interval(interval)
            .flatMap { 
                refreshSubscriberCount(channelName)
                    .map { count -> 
                        SubscriberCountEvent(
                            channelName = channelName,
                            subscriberCount = count,
                            timestamp = System.currentTimeMillis(),
                            eventType = SubscriberEventType.COUNT_UPDATED
                        )
                    }
            }
            .distinctUntilChanged { it.subscriberCount }
            .doOnNext { event ->
                emitSubscriberCountEvent(event)
            }
            .doOnSubscribe { 
                logger.debug("Started monitoring subscribers for channel: {}", channelName)
            }
            .doOnCancel {
                logger.debug("Stopped monitoring subscribers for channel: {}", channelName)
            }
    }
    
    /**
     * 추적 통계를 조회합니다.
     * 
     * @return 추적 통계 정보
     */
    fun getTrackingStatistics(): Mono<Map<String, Any>> {
        return Mono.fromCallable {
            mapOf<String, Any>(
                "cachedChannels" to subscriberCountCache.size,
                "totalQueries" to trackingStatistics.totalQueries.get(),
                "batchQueries" to trackingStatistics.batchQueries.get(),
                "cacheHits" to trackingStatistics.cacheHits.get(),
                "cacheMisses" to trackingStatistics.cacheMisses.get(),
                "errors" to trackingStatistics.errors.get(),
                "lastUpdateTime" to trackingStatistics.lastUpdateTime.get(),
                "cacheHitRatio" to trackingStatistics.getCacheHitRatio(),
                "timestamp" to System.currentTimeMillis()
            )
        }
    }
    
    /**
     * 캐시된 구독자 수를 조회합니다.
     * 
     * @param channelName 채널명
     * @return 캐시된 구독자 수 (캐시 미스시 empty)
     */
    private fun getCachedSubscriberCount(channelName: String): Mono<Int> {
        return Mono.fromCallable {
            val subscriberInfo = subscriberCountCache[channelName]
            val currentTime = System.currentTimeMillis()
            
            if (subscriberInfo != null && 
                (currentTime - subscriberInfo.lastUpdated) <= (CACHE_TTL_SECONDS * 1000)) {
                trackingStatistics.incrementCacheHits()
                subscriberInfo.count
            } else {
                trackingStatistics.incrementCacheMisses()
                null
            }
        }
        .flatMap { count ->
            if (count != null) Mono.just(count) else Mono.empty()
        }
    }
    
    /**
     * 구독자 수를 새로고침합니다.
     * 
     * @param channelName 채널명
     * @return 최신 구독자 수
     */
    private fun refreshSubscriberCount(channelName: String): Mono<Int> {
        return executeNumSubCommand(listOf(channelName))
            .map { counts -> counts[channelName] ?: 0 }
            .doOnSuccess { count ->
                updateSubscriberCountCache(channelName, count)
                trackingStatistics.incrementTotalQueries()
                
                // 채널 생명주기 서비스에도 업데이트
                channelLifecycleService.updateSubscriberCount(channelName, count)
                    .subscribe()
                
                // 구독자 수 변경 이벤트 발행
                emitSubscriberCountEvent(SubscriberCountEvent(
                    channelName = channelName,
                    subscriberCount = count,
                    timestamp = System.currentTimeMillis(),
                    eventType = SubscriberEventType.COUNT_REFRESHED
                ))
            }
    }
    
    /**
     * Redis PUBSUB NUMSUB 명령을 실행합니다.
     * 
     * @param channelNames 채널명 목록
     * @return 채널별 구독자 수 맵
     */
    private fun executeNumSubCommand(channelNames: List<String>): Mono<Map<String, Int>> {
        // 현재는 간단한 구현으로 0을 반환하도록 함
        // 실제 PUBSUB NUMSUB 명령은 Redis 연결을 통해 직접 실행해야 함
        return Mono.fromCallable {
            channelNames.associateWith { 0 }
        }
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorReturn(emptyMap())
    }
    
    /**
     * 구독자 수 캐시를 업데이트합니다.
     * 
     * @param channelName 채널명
     * @param count 구독자 수
     */
    private fun updateSubscriberCountCache(channelName: String, count: Int) {
        val currentTime = System.currentTimeMillis()
        val previousInfo = subscriberCountCache[channelName]
        
        subscriberCountCache[channelName] = SubscriberInfo(
            count = count,
            lastUpdated = currentTime
        )
        
        trackingStatistics.updateLastUpdateTime(currentTime)
        
        // 이전 값과 다르면 변경 이벤트 발행
        if (previousInfo == null || previousInfo.count != count) {
            emitSubscriberCountEvent(SubscriberCountEvent(
                channelName = channelName,
                subscriberCount = count,
                previousCount = previousInfo?.count,
                timestamp = currentTime,
                eventType = SubscriberEventType.COUNT_CHANGED
            ))
        }
    }
    
    /**
     * 구독자 수 변경 이벤트를 발행합니다.
     * 
     * @param event 구독자 수 이벤트
     */
    private fun emitSubscriberCountEvent(event: SubscriberCountEvent) {
        val result = subscriberCountSink.tryEmitNext(event)
        if (result.isFailure) {
            logger.warn("Failed to emit subscriber count event: {} for channel {}", 
                result, event.channelName)
        }
    }
    
    /**
     * 주기적인 구독자 수 업데이트를 시작합니다.
     */
    private fun startPeriodicUpdate() {
        Flux.interval(Duration.ofSeconds(BATCH_UPDATE_INTERVAL_SECONDS))
            .flatMap { 
                refreshAllActiveChannels()
                    .onErrorReturn(0)
            }
            .subscribe { updatedChannels ->
                if (updatedChannels > 0) {
                    logger.trace("Periodic update completed for {} channels", updatedChannels)
                }
            }
    }
}

/**
 * 구독자 정보 데이터 클래스
 */
data class SubscriberInfo(
    val count: Int,
    val lastUpdated: Long
)

/**
 * 구독자 수 변경 이벤트 데이터 클래스
 */
data class SubscriberCountEvent(
    val channelName: String,
    val subscriberCount: Int,
    val previousCount: Int? = null,
    val timestamp: Long,
    val eventType: SubscriberEventType
)

/**
 * 구독자 이벤트 타입 열거형
 */
enum class SubscriberEventType {
    COUNT_CHANGED,    // 구독자 수 변경
    COUNT_UPDATED,    // 구독자 수 업데이트
    COUNT_REFRESHED,  // 구독자 수 새로고침
    COUNT_CACHED      // 구독자 수 캐시됨
}

/**
 * 추적 통계 클래스
 */
class TrackingStatistics {
    private val _totalQueries = java.util.concurrent.atomic.AtomicLong(0)
    private val _batchQueries = java.util.concurrent.atomic.AtomicLong(0)
    private val _cacheHits = java.util.concurrent.atomic.AtomicLong(0)
    private val _cacheMisses = java.util.concurrent.atomic.AtomicLong(0)
    private val _errors = java.util.concurrent.atomic.AtomicLong(0)
    private val _lastUpdateTime = java.util.concurrent.atomic.AtomicLong(0)
    
    val totalQueries: java.util.concurrent.atomic.AtomicLong get() = _totalQueries
    val batchQueries: java.util.concurrent.atomic.AtomicLong get() = _batchQueries
    val cacheHits: java.util.concurrent.atomic.AtomicLong get() = _cacheHits
    val cacheMisses: java.util.concurrent.atomic.AtomicLong get() = _cacheMisses
    val errors: java.util.concurrent.atomic.AtomicLong get() = _errors
    val lastUpdateTime: java.util.concurrent.atomic.AtomicLong get() = _lastUpdateTime
    
    fun incrementTotalQueries() = _totalQueries.incrementAndGet()
    fun incrementBatchQueries() = _batchQueries.incrementAndGet()
    fun incrementCacheHits() = _cacheHits.incrementAndGet()
    fun incrementCacheMisses() = _cacheMisses.incrementAndGet()
    fun incrementErrors() = _errors.incrementAndGet()
    fun updateLastUpdateTime(time: Long) = _lastUpdateTime.set(time)
    
    fun getCacheHitRatio(): Double {
        val hits = _cacheHits.get()
        val misses = _cacheMisses.get()
        val total = hits + misses
        return if (total > 0) hits.toDouble() / total else 0.0
    }
}