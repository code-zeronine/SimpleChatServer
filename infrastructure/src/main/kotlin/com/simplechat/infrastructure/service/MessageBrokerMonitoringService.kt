package com.simplechat.infrastructure.service

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Instant

/**
 * 메시지 브로커 모니터링 서비스
 * 
 * Redis Pub/Sub 시스템과 WebSocket 세션의 상태를 모니터링하고
 * 성능 지표를 수집합니다.
 */
@Service
@ConditionalOnProperty(
    value = ["message-broker.monitoring.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class MessageBrokerMonitoringService(
    private val redisMessageBroker: RedisMessageBrokerService,
    private val sessionCacheService: WebSocketSessionCacheService,
    private val roomSubscriptionService: ChatRoomSubscriptionService,
    private val redisChannelMonitoringService: RedisChannelMonitoringService
) {
    
    private val logger = LoggerFactory.getLogger(MessageBrokerMonitoringService::class.java)
    
    /**
     * 5분마다 시스템 상태를 모니터링합니다.
     */
    @Scheduled(fixedRate = 300000) // 5분
    fun monitorSystemHealth() {
        collectSystemMetrics()
            .subscribe(
                { metrics ->
                    logger.info("System Health Metrics: {}", metrics)
                    
                    // 임계값 체크
                    checkThresholds(metrics)
                },
                { error ->
                    logger.error("Error collecting system metrics: {}", error.message, error)
                }
            )
    }
    
    /**
     * 1분마다 성능 지표를 수집합니다.
     */
    @Scheduled(fixedRate = 60000) // 1분
    fun collectPerformanceMetrics() {
        Mono.zip(
            sessionCacheService.getCacheStatistics(),
            roomSubscriptionService.getSubscriptionStatistics(),
            redisMessageBroker.getActiveSubscriptions()
        )
        .subscribe(
            { tuple ->
                val sessionStats = tuple.t1
                val subscriptionStats = tuple.t2
                val brokerStats = tuple.t3
                
                logger.debug("Performance Metrics - Sessions: {}, Subscriptions: {}, Broker: {}", 
                    sessionStats, subscriptionStats, brokerStats)
                
                // 성능 이슈 감지
                detectPerformanceIssues(
                    convertToMap(sessionStats), 
                    subscriptionStats, 
                    convertToMap(brokerStats)
                )
            },
            { error ->
                logger.error("Error collecting performance metrics: {}", error.message, error)
            }
        )
    }
    
    /**
     * 30분마다 비활성 세션과 구독을 정리합니다.
     */
    @Scheduled(fixedRate = 1800000) // 30분
    fun performMaintenanceTasks() {
        logger.info("Starting maintenance tasks")
        
        Mono.zip(
            sessionCacheService.cleanupExpiredSessions(),
            roomSubscriptionService.cleanupInactiveSubscriptions()
        )
        .subscribe(
            { tuple ->
                val cleanedSessions = tuple.t1
                val cleanedSubscriptions = tuple.t2
                
                logger.info("Maintenance completed - Cleaned sessions: {}, Cleaned subscriptions: {}", 
                    cleanedSessions, cleanedSubscriptions)
            },
            { error ->
                logger.error("Error during maintenance tasks: {}", error.message, error)
            }
        )
    }
    
    /**
     * 시스템 메트릭을 수집합니다.
     */
    private fun collectSystemMetrics(): Mono<Map<String, Any>> {
        return Mono.zip(
            sessionCacheService.getCacheStatistics(),
            roomSubscriptionService.getSubscriptionStatistics(),
            redisMessageBroker.getActiveSubscriptions()
        )
        .map { tuple ->
            val sessionStats = tuple.t1
            val subscriptionStats = tuple.t2
            val brokerStats = tuple.t3
            
            mapOf<String, Any>(
                "timestamp" to Instant.now().toString(),
                "sessions" to convertToMap(sessionStats),
                "subscriptions" to subscriptionStats,
                "messageBroker" to convertToMap(brokerStats),
                "systemInfo" to mapOf(
                    "jvmMemoryUsed" to Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(),
                    "jvmMemoryMax" to Runtime.getRuntime().maxMemory(),
                    "availableProcessors" to Runtime.getRuntime().availableProcessors()
                )
            )
        }
    }
    
    /**
     * 임계값을 체크하고 경고를 발생시킵니다.
     */
    private fun checkThresholds(metrics: Map<String, Any>) {
        @Suppress("UNCHECKED_CAST")
        val sessionStats = metrics["sessions"] as? Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val subscriptionStats = metrics["subscriptions"] as? Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val brokerStats = metrics["messageBroker"] as? Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val systemInfo = metrics["systemInfo"] as? Map<String, Any>
        
        // 세션 수 임계값 체크
        val sessionCount = sessionStats?.get("localCacheSize") as? Int ?: 0
        if (sessionCount > 10000) {
            logger.warn("HIGH SESSION COUNT WARNING: {} active sessions", sessionCount)
        }
        
        // 구독 수 임계값 체크
        val totalSubscriptions = subscriptionStats?.get("totalSubscriptions") as? Int ?: 0
        if (totalSubscriptions > 50000) {
            logger.warn("HIGH SUBSCRIPTION COUNT WARNING: {} active subscriptions", totalSubscriptions)
        }
        
        // 메모리 사용량 체크
        val memoryUsed = systemInfo?.get("jvmMemoryUsed") as? Long ?: 0L
        val memoryMax = systemInfo?.get("jvmMemoryMax") as? Long ?: Long.MAX_VALUE
        val memoryUsagePercent = if (memoryMax > 0) (memoryUsed.toDouble() / memoryMax * 100) else 0.0
        
        if (memoryUsagePercent > 80.0) {
            logger.warn("HIGH MEMORY USAGE WARNING: {:.2f}% memory used", memoryUsagePercent)
        }
    }
    
    /**
     * 성능 이슈를 감지합니다.
     */
    private fun detectPerformanceIssues(
        sessionStats: Map<String, Any>,
        subscriptionStats: Map<String, Any>,
        brokerStats: Map<String, Any>
    ) {
        // 평균 구독 수 분석
        val averageRoomsPerUser = subscriptionStats["averageRoomsPerUser"] as? Double ?: 0.0
        if (averageRoomsPerUser > 50.0) {
            logger.warn("PERFORMANCE WARNING: High average rooms per user: {:.2f}", averageRoomsPerUser)
        }
        
        // 활성 브로커 구독 수 분석
        val activeStreamsCount = brokerStats["activeStreamsCount"] as? Int ?: 0
        val activeSubscriptionsCount = brokerStats["activeSubscriptionsCount"] as? Int ?: 0
        
        if (activeStreamsCount != activeSubscriptionsCount) {
            logger.warn("CONSISTENCY WARNING: Stream/Subscription count mismatch - Streams: {}, Subscriptions: {}", 
                activeStreamsCount, activeSubscriptionsCount)
        }
        
        // 활성 구독 일관성 검사
        if (activeStreamsCount > 0 && activeSubscriptionsCount > 0 && 
            Math.abs(activeStreamsCount - activeSubscriptionsCount) > 10) {
            logger.warn("PERFORMANCE WARNING: Large discrepancy between streams and subscriptions")
        }
    }
    
    /**
     * 강제로 시스템 메트릭을 수집하고 반환합니다.
     */
    fun getSystemHealthReport(): Mono<Map<String, Any>> {
        return collectSystemMetrics()
            .doOnSuccess { metrics ->
                logger.info("System health report requested: {}", metrics)
            }
    }
    
    /**
     * 타입된 객체를 Map으로 변환하는 헬퍼 메서드
     */
    private fun convertToMap(obj: Any): Map<String, Any> {
        return when (obj) {
            is SessionCacheStatistics -> mapOf(
                "localCacheSize" to obj.localCacheSize,
                "timestamp" to obj.timestamp.toString()
            )
            is Map<*, *> -> obj as Map<String, Any>
            else -> mapOf("value" to obj.toString())
        }
    }
}