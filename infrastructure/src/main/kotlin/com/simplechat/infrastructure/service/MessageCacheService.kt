package com.simplechat.infrastructure.service

import com.simplechat.domain.entity.ChatMessage
import com.simplechat.infrastructure.monitoring.CustomMetricsService
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant

/**
 * 메시지 캐싱 전용 서비스
 * 
 * Redis를 활용한 메시지 캐싱 전략을 구현합니다:
 * - 최근 메시지 캐싱 (채팅방별 최근 50개)
 * - 캐시 키 네이밍 규칙 및 TTL 관리
 * - 캐시 무효화 전략
 */
@Service
class MessageCacheService(
    private val reactiveRedisTemplate: ReactiveRedisTemplate<String, Any>,
    private val customMetricsService: CustomMetricsService
) {

    private val logger = LoggerFactory.getLogger(MessageCacheService::class.java)

    companion object {
        private const val RECENT_MESSAGES_KEY_PREFIX = "chat:recent_messages:"
        private const val MESSAGE_COUNT_KEY_PREFIX = "chat:message_count:"
        private const val CACHE_STATS_KEY_PREFIX = "cache:stats:"
        private const val DEFAULT_RECENT_MESSAGES_SIZE = 50
        private const val RECENT_MESSAGES_TTL_HOURS = 6L
        private const val MESSAGE_COUNT_TTL_HOURS = 12L
    }

    /**
     * 채팅방의 최근 메시지를 캐시에서 조회
     */
    fun getRecentMessages(roomId: Long, size: Int = DEFAULT_RECENT_MESSAGES_SIZE): Flux<ChatMessage> {
        val cacheKey = createRecentMessagesCacheKey(roomId)
        val startTime = Instant.now()
        
        return reactiveRedisTemplate.opsForList()
            .range(cacheKey, 0, size - 1L)
            .cast(ChatMessage::class.java)
            .doOnSubscribe { 
                logger.debug("Fetching recent messages from cache: roomId={}, size={}", roomId, size)
            }
            .doOnComplete {
                customMetricsService.recordCacheHit()
                customMetricsService.recordCacheLookupTime(Duration.between(startTime, Instant.now()))
                incrementCacheHit(roomId)
                logger.debug("Cache hit for recent messages: roomId={}", roomId)
            }
            .onErrorResume { error ->
                logger.warn("Cache miss for recent messages: roomId={}, error={}", roomId, error.message)
                customMetricsService.recordCacheMiss()
                incrementCacheMiss(roomId)
                Flux.empty()
            }
    }

    /**
     * 최근 메시지를 캐시에 저장
     */
    fun cacheRecentMessages(roomId: Long, messages: List<ChatMessage>): Mono<Void> {
        if (messages.isEmpty()) {
            return Mono.empty()
        }

        val cacheKey = createRecentMessagesCacheKey(roomId)
        
        return reactiveRedisTemplate.opsForList()
            .delete(cacheKey) // 기존 캐시 삭제
            .then(
                reactiveRedisTemplate.opsForList()
                    .rightPushAll(cacheKey, messages.toTypedArray())
            )
            .then(
                reactiveRedisTemplate.expire(cacheKey, Duration.ofHours(RECENT_MESSAGES_TTL_HOURS))
            )
            .then()
            .doOnSuccess {
                logger.debug("Cached recent messages: roomId={}, count={}", roomId, messages.size)
            }
            .doOnError { error ->
                logger.error("Failed to cache recent messages: roomId={}, error={}", roomId, error.message, error)
            }
    }

    /**
     * 새 메시지를 캐시에 추가하고 오래된 메시지 제거
     */
    fun addNewMessageToCache(roomId: Long, message: ChatMessage): Mono<Void> {
        val cacheKey = createRecentMessagesCacheKey(roomId)
        
        return reactiveRedisTemplate.opsForList()
            .leftPush(cacheKey, message) // 최신 메시지를 앞에 추가
            .then(
                reactiveRedisTemplate.opsForList()
                    .trim(cacheKey, 0, DEFAULT_RECENT_MESSAGES_SIZE - 1L) // 최대 크기 유지
            )
            .then(
                reactiveRedisTemplate.expire(cacheKey, Duration.ofHours(RECENT_MESSAGES_TTL_HOURS))
            )
            .then()
            .doOnSuccess {
                logger.debug("Added new message to cache: roomId={}, messageId={}", roomId, message.id)
            }
            .doOnError { error ->
                logger.error("Failed to add new message to cache: roomId={}, error={}", roomId, error.message, error)
            }
    }

    /**
     * 채팅방의 메시지 개수를 캐시에서 조회
     */
    fun getCachedMessageCount(roomId: Long): Mono<Long> {
        val cacheKey = createMessageCountCacheKey(roomId)
        
        return reactiveRedisTemplate.opsForValue()
            .get(cacheKey)
            .map { it as Number }
            .map { it.toLong() }
            .doOnNext { count ->
                incrementCacheHit(roomId)
                logger.debug("Cache hit for message count: roomId={}, count={}", roomId, count)
            }
            .switchIfEmpty(
                Mono.fromRunnable<Long> { 
                    incrementCacheMiss(roomId)
                    logger.debug("Cache miss for message count: roomId={}", roomId)
                }.then(Mono.empty())
            )
    }

    /**
     * 메시지 개수를 캐시에 저장
     */
    fun cacheMessageCount(roomId: Long, count: Long): Mono<Void> {
        val cacheKey = createMessageCountCacheKey(roomId)
        
        return reactiveRedisTemplate.opsForValue()
            .set(cacheKey, count, Duration.ofHours(MESSAGE_COUNT_TTL_HOURS))
            .then()
            .doOnSuccess {
                logger.debug("Cached message count: roomId={}, count={}", roomId, count)
            }
            .doOnError { error ->
                logger.error("Failed to cache message count: roomId={}, error={}", roomId, error.message, error)
            }
    }

    /**
     * 메시지 개수 증가 (새 메시지 추가 시)
     */
    fun incrementMessageCount(roomId: Long): Mono<Long> {
        val cacheKey = createMessageCountCacheKey(roomId)
        
        return reactiveRedisTemplate.opsForValue()
            .increment(cacheKey)
            .then(
                reactiveRedisTemplate.expire(cacheKey, Duration.ofHours(MESSAGE_COUNT_TTL_HOURS))
                    .then(reactiveRedisTemplate.opsForValue().get(cacheKey).map { it as Number }.map { it.toLong() })
            )
            .doOnNext { newCount ->
                logger.debug("Incremented message count: roomId={}, newCount={}", roomId, newCount)
            }
    }

    /**
     * 특정 채팅방의 모든 캐시 무효화
     */
    fun invalidateRoomCache(roomId: Long): Mono<Void> {
        val recentMessagesKey = createRecentMessagesCacheKey(roomId)
        val messageCountKey = createMessageCountCacheKey(roomId)
        
        return Flux.fromIterable(listOf(recentMessagesKey, messageCountKey))
            .flatMap { cacheKey ->
                reactiveRedisTemplate.opsForValue().delete(cacheKey)
            }
            .then()
            .doOnSuccess {
                logger.debug("Invalidated all cache for room: {}", roomId)
            }
            .doOnError { error ->
                logger.error("Failed to invalidate room cache: roomId={}, error={}", roomId, error.message, error)
            }
    }

    /**
     * 캐시 통계 조회
     */
    fun getCacheStats(roomId: Long): Mono<CacheStats> {
        val hitKey = createCacheStatsKey(roomId, "hit")
        val missKey = createCacheStatsKey(roomId, "miss")
        
        return Mono.zip(
            reactiveRedisTemplate.opsForValue().get(hitKey).map { it as Number }.map { it.toLong() }.defaultIfEmpty(0L),
            reactiveRedisTemplate.opsForValue().get(missKey).map { it as Number }.map { it.toLong() }.defaultIfEmpty(0L)
        ).map { tuple ->
            val hits = tuple.t1
            val misses = tuple.t2
            val total = hits + misses
            val hitRate = if (total > 0) hits.toDouble() / total * 100 else 0.0
            
            CacheStats(
                roomId = roomId,
                hits = hits,
                misses = misses,
                hitRate = hitRate
            )
        }
    }

    /**
     * 캐시 통계 초기화
     */
    fun resetCacheStats(roomId: Long): Mono<Void> {
        val hitKey = createCacheStatsKey(roomId, "hit")
        val missKey = createCacheStatsKey(roomId, "miss")
        
        return Flux.fromIterable(listOf(hitKey, missKey))
            .flatMap { key ->
                reactiveRedisTemplate.opsForValue().delete(key)
            }
            .then()
    }

    // ===========================================
    // Private Helper Methods
    // ===========================================

    private fun createRecentMessagesCacheKey(roomId: Long): String {
        return "${RECENT_MESSAGES_KEY_PREFIX}$roomId"
    }

    private fun createMessageCountCacheKey(roomId: Long): String {
        return "${MESSAGE_COUNT_KEY_PREFIX}$roomId"
    }

    private fun createCacheStatsKey(roomId: Long, type: String): String {
        return "${CACHE_STATS_KEY_PREFIX}${roomId}:$type"
    }

    private fun incrementCacheHit(roomId: Long): Mono<Void> {
        val hitKey = createCacheStatsKey(roomId, "hit")
        return reactiveRedisTemplate.opsForValue()
            .increment(hitKey)
            .then()
    }

    private fun incrementCacheMiss(roomId: Long): Mono<Void> {
        val missKey = createCacheStatsKey(roomId, "miss")
        return reactiveRedisTemplate.opsForValue()
            .increment(missKey)
            .then()
    }

    /**
     * 캐시 통계 데이터 클래스
     */
    data class CacheStats(
        val roomId: Long,
        val hits: Long,
        val misses: Long,
        val hitRate: Double
    )
}