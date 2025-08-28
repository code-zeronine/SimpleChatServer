package com.simplechat.infrastructure.service

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * WebSocket 세션 정보 Redis 캐싱 서비스
 * 
 * WebSocket 세션 정보와 사용자 온라인 상태를 Redis에 캐싱하여 분산 환경에서
 * 세션 관리 및 온라인 상태 추적을 제공합니다.
 */
@Service
class WebSocketSessionCacheService(
    private val reactiveRedisTemplate: ReactiveRedisTemplate<String, Any>,
    private val objectMapper: ObjectMapper
) {
    
    private val logger = LoggerFactory.getLogger(WebSocketSessionCacheService::class.java)
    
    // 로컬 세션 추적 (빠른 조회용)
    private val localSessionCache = ConcurrentHashMap<String, WebSocketSessionInfo>()
    
    companion object {
        // Redis 키 패턴
        const val SESSION_KEY_PREFIX = "websocket:session:"
        const val USER_ONLINE_KEY_PREFIX = "user:online:"
        const val USER_SESSIONS_KEY_PREFIX = "user:sessions:"
        const val SESSION_INDEX_KEY_PREFIX = "session:index:"
        
        // TTL 설정
        val DEFAULT_SESSION_TTL = Duration.ofHours(24)
        val USER_ONLINE_TTL = Duration.ofMinutes(30)
        val SESSION_HEARTBEAT_INTERVAL = Duration.ofMinutes(5)
        
        // 배치 처리 설정
        const val CLEANUP_BATCH_SIZE = 100
        val CLEANUP_INTERVAL = Duration.ofMinutes(10)
    }
    
    @PostConstruct
    fun init() {
        logger.info("WebSocket session cache service initialized")
    }
    
    @PreDestroy
    fun destroy() {
        localSessionCache.clear()
        logger.info("WebSocket session cache service destroyed")
    }
    
    /**
     * WebSocket 세션 정보를 Redis에 캐싱합니다.
     * 
     * @param sessionInfo WebSocket 세션 정보
     * @return 저장 성공 여부
     */
    fun cacheSessionInfo(sessionInfo: WebSocketSessionInfo): Mono<Boolean> {
        val sessionKey = "$SESSION_KEY_PREFIX${sessionInfo.sessionId}"
        val userOnlineKey = "$USER_ONLINE_KEY_PREFIX${sessionInfo.userId}"
        val userSessionsKey = "$USER_SESSIONS_KEY_PREFIX${sessionInfo.userId}"
        val sessionIndexKey = "$SESSION_INDEX_KEY_PREFIX${sessionInfo.sessionId}"
        
        return Mono.fromCallable {
            objectMapper.writeValueAsString(sessionInfo)
        }
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap { sessionJson ->
            // 여러 Redis 연산을 병렬로 수행
            val sessionOp = reactiveRedisTemplate.opsForValue()
                .set(sessionKey, sessionJson, DEFAULT_SESSION_TTL)
            
            val userOnlineOp = reactiveRedisTemplate.opsForValue()
                .set(userOnlineKey, sessionInfo.toOnlineStatus(objectMapper), USER_ONLINE_TTL)
            
            val userSessionsOp = reactiveRedisTemplate.opsForSet()
                .add(userSessionsKey, sessionInfo.sessionId)
                .flatMap { 
                    reactiveRedisTemplate.expire(userSessionsKey, DEFAULT_SESSION_TTL)
                }
            
            val sessionIndexOp = reactiveRedisTemplate.opsForHash<String, Any>()
                .put(sessionIndexKey, "userId", sessionInfo.userId)
                .flatMap {
                    reactiveRedisTemplate.expire(sessionIndexKey, DEFAULT_SESSION_TTL)
                }
            
            // 모든 연산 병렬 실행
            Mono.zip(sessionOp, userOnlineOp, userSessionsOp, sessionIndexOp)
                .map { tuple -> tuple.t1 && tuple.t2 && tuple.t3 && tuple.t4 }
        }
        .doOnSuccess { success ->
            if (success) {
                localSessionCache[sessionInfo.sessionId] = sessionInfo
                logger.debug("Session cached successfully: {}", sessionInfo.sessionId)
            } else {
                logger.warn("Failed to cache session: {}", sessionInfo.sessionId)
            }
        }
        .doOnError { error ->
            logger.error("Error caching session {}: {}", sessionInfo.sessionId, error.message, error)
        }
        .onErrorReturn(false)
    }
    
    /**
     * 세션 ID로 WebSocket 세션 정보를 조회합니다.
     * 
     * @param sessionId 세션 ID
     * @return WebSocket 세션 정보
     */
    fun getSessionInfo(sessionId: String): Mono<WebSocketSessionInfo> {
        // 로컬 캐시에서 먼저 확인
        localSessionCache[sessionId]?.let { sessionInfo ->
            if (sessionInfo.isExpired()) {
                localSessionCache.remove(sessionId)
            } else {
                return Mono.just(sessionInfo)
            }
        }
        
        val sessionKey = "$SESSION_KEY_PREFIX$sessionId"
        
        return reactiveRedisTemplate.opsForValue()
            .get(sessionKey)
            .cast(String::class.java)
            .flatMap { sessionJson ->
                Mono.fromCallable {
                    objectMapper.readValue(sessionJson, WebSocketSessionInfo::class.java)
                }
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess { sessionInfo ->
                    // TTL 연장 (활성 세션 표시)
                    reactiveRedisTemplate.expire(sessionKey, DEFAULT_SESSION_TTL)
                        .subscribe()
                    
                    // 로컬 캐시 업데이트
                    localSessionCache[sessionId] = sessionInfo
                    logger.trace("Session retrieved from Redis: {}", sessionId)
                }
            }
            .doOnError { error ->
                logger.error("Error retrieving session {}: {}", sessionId, error.message, error)
            }
            .onErrorComplete()
    }
    
    /**
     * 사용자 ID로 모든 활성 세션을 조회합니다.
     * 
     * @param userId 사용자 ID
     * @return 사용자의 활성 세션 목록
     */
    fun getUserSessions(userId: Long): Flux<WebSocketSessionInfo> {
        val userSessionsKey = "$USER_SESSIONS_KEY_PREFIX$userId"
        
        return reactiveRedisTemplate.opsForSet()
            .members(userSessionsKey)
            .cast(String::class.java)
            .flatMap { sessionId ->
                getSessionInfo(sessionId)
            }
            .doOnNext { sessionInfo ->
                logger.trace("Found active session for user {}: {}", userId, sessionInfo.sessionId)
            }
    }
    
    /**
     * 사용자 온라인 상태를 확인합니다.
     * 
     * @param userId 사용자 ID
     * @return 온라인 상태 정보
     */
    fun getUserOnlineStatus(userId: Long): Mono<UserOnlineStatus> {
        val userOnlineKey = "$USER_ONLINE_KEY_PREFIX$userId"
        
        return reactiveRedisTemplate.opsForValue()
            .get(userOnlineKey)
            .cast(String::class.java)
            .flatMap { statusJson ->
                Mono.fromCallable {
                    objectMapper.readValue(statusJson, UserOnlineStatus::class.java)
                }
                .subscribeOn(Schedulers.boundedElastic())
            }
            .doOnNext { status ->
                logger.trace("User {} online status: {}", userId, status.isOnline)
            }
            .doOnError { error ->
                logger.error("Error getting online status for user {}: {}", userId, error.message, error)
            }
            .onErrorComplete()
    }
    
    /**
     * 세션 하트비트를 업데이트하여 TTL을 연장합니다.
     * 
     * @param sessionId 세션 ID
     * @return 업데이트 성공 여부
     */
    fun updateSessionHeartbeat(sessionId: String): Mono<Boolean> {
        return getSessionInfo(sessionId)
            .flatMap { sessionInfo ->
                val updatedSessionInfo = sessionInfo.copy(
                    lastActivityAt = Instant.now(),
                    heartbeatCount = sessionInfo.heartbeatCount + 1
                )
                cacheSessionInfo(updatedSessionInfo)
            }
            .switchIfEmpty(Mono.just(false))
    }
    
    /**
     * WebSocket 세션을 제거하고 온라인 상태를 업데이트합니다.
     * 
     * @param sessionId 세션 ID
     * @return 제거 성공 여부
     */
    fun removeSession(sessionId: String): Mono<Boolean> {
        return getSessionInfo(sessionId)
            .flatMap { sessionInfo ->
                val sessionKey = "$SESSION_KEY_PREFIX$sessionId"
                val userSessionsKey = "$USER_SESSIONS_KEY_PREFIX${sessionInfo.userId}"
                val sessionIndexKey = "$SESSION_INDEX_KEY_PREFIX$sessionId"
                
                val deleteSessionOp = reactiveRedisTemplate.delete(sessionKey)
                val removeFromUserSessionsOp = reactiveRedisTemplate.opsForSet()
                    .remove(userSessionsKey, sessionId)
                val deleteIndexOp = reactiveRedisTemplate.delete(sessionIndexKey)
                
                Mono.zip(deleteSessionOp, removeFromUserSessionsOp, deleteIndexOp)
                    .flatMap { 
                        // 사용자의 다른 세션이 있는지 확인
                        checkAndUpdateUserOnlineStatus(sessionInfo.userId)
                    }
                    .doOnSuccess { 
                        localSessionCache.remove(sessionId)
                        logger.debug("Session removed: {}", sessionId)
                    }
            }
            .switchIfEmpty(Mono.just(true))
            .doOnError { error ->
                logger.error("Error removing session {}: {}", sessionId, error.message, error)
            }
            .onErrorReturn(false)
    }
    
    /**
     * 사용자의 온라인 상태를 업데이트합니다.
     * 
     * @param userId 사용자 ID
     * @return 업데이트 성공 여부
     */
    private fun checkAndUpdateUserOnlineStatus(userId: Long): Mono<Boolean> {
        return getUserSessions(userId)
            .hasElements()
            .flatMap { hasActiveSessions ->
                val userOnlineKey = "$USER_ONLINE_KEY_PREFIX$userId"
                
                if (hasActiveSessions) {
                    // 활성 세션이 있으면 온라인 상태 유지
                    val onlineStatus = UserOnlineStatus(
                        userId = userId,
                        isOnline = true,
                        lastSeenAt = Instant.now(),
                        sessionCount = 1 // 정확한 카운트는 별도 로직으로
                    )
                    
                    reactiveRedisTemplate.opsForValue()
                        .set(userOnlineKey, objectMapper.writeValueAsString(onlineStatus), USER_ONLINE_TTL)
                } else {
                    // 활성 세션이 없으면 오프라인 처리
                    reactiveRedisTemplate.delete(userOnlineKey)
                        .map { it > 0 }
                }
            }
    }
    
    /**
     * 만료된 세션들을 정리합니다.
     * 
     * @return 정리된 세션 수
     */
    fun cleanupExpiredSessions(): Mono<Long> {
        return Mono.fromCallable {
            val expiredSessions = localSessionCache.values
                .filter { it.isExpired() }
                .map { it.sessionId }
            
            expiredSessions.forEach { sessionId ->
                localSessionCache.remove(sessionId)
            }
            
            expiredSessions.size.toLong()
        }
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap { localCleanupCount ->
            // Redis에서도 만료된 세션 패턴 검색 (SCAN 사용)
            cleanupExpiredSessionsFromRedis()
                .map { it + localCleanupCount }
        }
        .doOnSuccess { cleanupCount ->
            if (cleanupCount > 0) {
                logger.info("Cleaned up {} expired sessions", cleanupCount)
            }
        }
        .doOnError { error ->
            logger.error("Error during session cleanup: {}", error.message, error)
        }
        .onErrorReturn(0L)
    }
    
    /**
     * Redis에서 만료된 세션을 정리합니다.
     */
    private fun cleanupExpiredSessionsFromRedis(): Mono<Long> {
        // 실제 구현에서는 Redis SCAN을 사용하여 효율적으로 처리
        // 여기서는 간단한 구현으로 대체
        return Mono.just(0L)
    }
    
    /**
     * 전체 캐시 통계를 조회합니다.
     * 
     * @return 캐시 통계 정보
     */
    fun getCacheStatistics(): Mono<SessionCacheStatistics> {
        return Mono.fromCallable {
            SessionCacheStatistics(
                localCacheSize = localSessionCache.size,
                timestamp = Instant.now()
            )
        }
        .subscribeOn(Schedulers.boundedElastic())
        .doOnSuccess { stats ->
            logger.debug("Cache statistics: {}", stats)
        }
    }
}

/**
 * WebSocket 세션 정보 데이터 클래스
 */
data class WebSocketSessionInfo(
    val sessionId: String,
    val userId: Long,
    val remoteAddress: String?,
    val userAgent: String?,
    val connectedAt: Instant,
    val lastActivityAt: Instant,
    val heartbeatCount: Long = 0,
    val attributes: Map<String, String> = emptyMap()
) {
    /**
     * 세션이 만료되었는지 확인합니다.
     */
    @JsonIgnore
    fun isExpired(): Boolean {
        val expiryThreshold = Instant.now().minus(WebSocketSessionCacheService.DEFAULT_SESSION_TTL)
        return lastActivityAt.isBefore(expiryThreshold)
    }
    
    /**
     * 온라인 상태 객체로 변환합니다.
     */
    fun toOnlineStatus(objectMapper: ObjectMapper): String {
        val status = UserOnlineStatus(
            userId = userId,
            isOnline = true,
            lastSeenAt = lastActivityAt,
            sessionCount = 1
        )
        return objectMapper.writeValueAsString(status)
    }
}

/**
 * 사용자 온라인 상태 데이터 클래스
 */
data class UserOnlineStatus(
    val userId: Long,
    val isOnline: Boolean,
    val lastSeenAt: Instant,
    val sessionCount: Int = 0,
    val platform: String? = null
)

/**
 * 세션 캐시 통계 데이터 클래스
 */
data class SessionCacheStatistics(
    val localCacheSize: Int,
    val timestamp: Instant
)