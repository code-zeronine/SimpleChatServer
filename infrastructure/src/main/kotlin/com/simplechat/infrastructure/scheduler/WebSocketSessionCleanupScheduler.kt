package com.simplechat.infrastructure.scheduler

import com.simplechat.infrastructure.service.WebSocketSessionCacheService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration

/**
 * WebSocket 세션 캐시 정리 스케줄러
 * 
 * 주기적으로 만료된 세션들을 정리하고 캐시 통계를 기록합니다.
 */
@Component
@ConditionalOnProperty(
    value = ["websocket.session.cleanup.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class WebSocketSessionCleanupScheduler(
    private val sessionCacheService: WebSocketSessionCacheService
) {
    
    private val logger = LoggerFactory.getLogger(WebSocketSessionCleanupScheduler::class.java)
    
    companion object {
        // 10분마다 정리 작업 수행
        const val CLEANUP_CRON = "0 */10 * * * *"
        // 1시간마다 통계 로그 출력
        const val STATISTICS_CRON = "0 0 * * * *"
    }
    
    /**
     * 만료된 세션들을 주기적으로 정리합니다.
     */
    @Scheduled(cron = CLEANUP_CRON)
    fun cleanupExpiredSessions() {
        sessionCacheService.cleanupExpiredSessions()
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                { cleanupCount ->
                    if (cleanupCount > 0) {
                        logger.info("Scheduled cleanup completed. Removed {} expired sessions", cleanupCount)
                    } else {
                        logger.debug("Scheduled cleanup completed. No expired sessions found")
                    }
                },
                { error ->
                    logger.error("Error during scheduled session cleanup: {}", error.message, error)
                }
            )
    }
    
    /**
     * 캐시 통계를 주기적으로 로그에 출력합니다.
     */
    @Scheduled(cron = STATISTICS_CRON)
    fun logCacheStatistics() {
        sessionCacheService.getCacheStatistics()
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                { stats ->
                    logger.info("WebSocket session cache statistics - Local cache size: {}, Timestamp: {}", 
                        stats.localCacheSize, stats.timestamp)
                },
                { error ->
                    logger.error("Error retrieving cache statistics: {}", error.message, error)
                }
            )
    }
    
    /**
     * 애플리케이션 시작 시 초기 정리 작업을 수행합니다.
     */
    @Scheduled(initialDelay = 30000, fixedRate = Long.MAX_VALUE)
    fun initialCleanup() {
        logger.info("Performing initial session cache cleanup")
        
        sessionCacheService.cleanupExpiredSessions()
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                { cleanupCount ->
                    logger.info("Initial cleanup completed. Removed {} expired sessions", cleanupCount)
                },
                { error ->
                    logger.error("Error during initial session cleanup: {}", error.message, error)
                }
            )
    }
}