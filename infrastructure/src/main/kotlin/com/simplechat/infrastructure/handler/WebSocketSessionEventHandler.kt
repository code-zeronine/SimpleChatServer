package com.simplechat.infrastructure.handler

import com.simplechat.infrastructure.service.WebSocketSessionCacheService
import com.simplechat.infrastructure.service.WebSocketSessionInfo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.time.Instant

/**
 * WebSocket 세션 이벤트를 처리하여 Redis 캐시를 자동으로 관리합니다.
 */
@Component
class WebSocketSessionEventHandler(
    private val sessionCacheService: WebSocketSessionCacheService
) {
    
    private val logger = LoggerFactory.getLogger(WebSocketSessionEventHandler::class.java)
    
    /**
     * WebSocket 연결 생성 시 세션 정보를 캐시에 저장합니다.
     */
    fun handleSessionConnected(
        sessionId: String,
        userId: Long,
        remoteAddress: String? = null,
        userAgent: String? = null,
        attributes: Map<String, String> = emptyMap()
    ): Mono<Void> {
        val sessionInfo = WebSocketSessionInfo(
            sessionId = sessionId,
            userId = userId,
            remoteAddress = remoteAddress,
            userAgent = userAgent,
            connectedAt = Instant.now(),
            lastActivityAt = Instant.now(),
            attributes = attributes
        )
        
        return sessionCacheService.cacheSessionInfo(sessionInfo)
            .doOnSuccess { success ->
                if (success) {
                    logger.info("Session cached for user {}: {}", userId, sessionId)
                } else {
                    logger.warn("Failed to cache session for user {}: {}", userId, sessionId)
                }
            }
            .doOnError { error ->
                logger.error("Error caching session for user {}: {}", userId, error.message, error)
            }
            .then()
    }
    
    /**
     * WebSocket 연결 종료 시 세션 정보를 캐시에서 제거합니다.
     */
    fun handleSessionDisconnected(sessionId: String): Mono<Void> {
        return sessionCacheService.removeSession(sessionId)
            .doOnSuccess { success ->
                if (success) {
                    logger.info("Session removed from cache: {}", sessionId)
                } else {
                    logger.warn("Failed to remove session from cache: {}", sessionId)
                }
            }
            .doOnError { error ->
                logger.error("Error removing session from cache {}: {}", sessionId, error.message, error)
            }
            .then()
    }
    
    /**
     * WebSocket 활동 시 하트비트를 업데이트합니다.
     */
    fun handleSessionActivity(sessionId: String): Mono<Void> {
        return sessionCacheService.updateSessionHeartbeat(sessionId)
            .doOnSuccess { success ->
                if (success) {
                    logger.trace("Heartbeat updated for session: {}", sessionId)
                } else {
                    logger.debug("Session not found for heartbeat update: {}", sessionId)
                }
            }
            .doOnError { error ->
                logger.error("Error updating heartbeat for session {}: {}", sessionId, error.message, error)
            }
            .then()
    }
    
    
    
    /**
     * 캐시 통계를 로그로 출력합니다.
     */
    fun logCacheStatistics(): Mono<Void> {
        return sessionCacheService.getCacheStatistics()
            .doOnNext { stats ->
                logger.info("Session cache statistics - Local cache size: {}, Timestamp: {}", 
                    stats.localCacheSize, stats.timestamp)
            }
            .then()
    }
}