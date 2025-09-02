package com.simplechat.infrastructure.service

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 통합된 Redis 모니터링 서비스
 * 
 * 기존의 Redis 모니터링 관련 서비스들을 하나로 통합:
 * - RedisChannelMonitoringService
 * - MessageBrokerMonitoringService (Redis 관련 부분)
 * - RedisConnectionService
 */
@Service
class RedisMonitoringService(
    private val redisTemplate: ReactiveRedisTemplate<String, Any>,
    private val connectionFactory: ReactiveRedisConnectionFactory,
    private val redisMessageBrokerService: RedisMessageBrokerService,
    private val channelManagerService: RedisChannelManagerService
) {

    private val logger = LoggerFactory.getLogger(RedisMonitoringService::class.java)
    
    // 연결 상태 추적
    private var lastConnectionCheck = Instant.now()
    private val connectionCheckInterval = Duration.ofSeconds(30)
    private val connectionErrors = AtomicLong(0)
    private val connectionSuccesses = AtomicLong(0)
    
    // 성능 메트릭
    private val performanceMetrics = ConcurrentHashMap<String, AtomicLong>()
    private val latencyMeasurements = ConcurrentHashMap<String, Long>()

    @PostConstruct
    fun initialize() {
        logger.info("Redis Monitoring Service initialized")
        startPeriodicHealthChecks()
        startPerformanceMonitoring()
    }

    @PreDestroy
    fun cleanup() {
        performanceMetrics.clear()
        latencyMeasurements.clear()
        logger.info("Redis Monitoring Service cleaned up")
    }

    /**
     * 주기적 헬스 체크 시작
     */
    private fun startPeriodicHealthChecks() {
        Flux.interval(connectionCheckInterval)
            .flatMap { performHealthCheck() }
            .subscribe(
                { result ->
                    if (result["status"] == "UP") {
                        connectionSuccesses.incrementAndGet()
                    } else {
                        connectionErrors.incrementAndGet()
                        logger.warn("Redis health check failed: {}", result)
                    }
                },
                { error ->
                    connectionErrors.incrementAndGet()
                    logger.error("Redis health check error", error)
                }
            )
    }

    /**
     * 성능 모니터링 시작
     */
    private fun startPerformanceMonitoring() {
        Flux.interval(Duration.ofMinutes(1))
            .flatMap { collectPerformanceMetrics() }
            .subscribe(
                { metrics -> logger.debug("Performance metrics collected: {}", metrics) },
                { error -> logger.error("Error collecting performance metrics", error) }
            )
    }

    /**
     * 헬스 체크 수행
     */
    fun performHealthCheck(): Mono<Map<String, Any>> {
        val startTime = System.currentTimeMillis()
        
        return checkRedisConnection()
            .map { connected ->
                val latency = System.currentTimeMillis() - startTime
                latencyMeasurements["health_check"] = latency
                
                val messageServiceStats = redisMessageBrokerService.getStatistics()
                val channelManagerStats = channelManagerService.getStatistics()
                
                mapOf(
                    "status" to if (connected) "UP" else "DOWN",
                    "redis_connected" to connected,
                    "connection_latency_ms" to latency,
                    "connection_errors" to connectionErrors.get(),
                    "connection_successes" to connectionSuccesses.get(),
                    "last_check" to Instant.now().toString(),
                    "message_service" to messageServiceStats,
                    "channel_manager" to channelManagerStats,
                    "uptime_check" to connected
                )
            }
            .doOnError { error ->
                logger.error("Health check failed", error)
                connectionErrors.incrementAndGet()
            }
            .onErrorReturn(
                mapOf(
                    "status" to "DOWN",
                    "redis_connected" to false,
                    "error" to "Health check failed",
                    "connection_errors" to connectionErrors.get()
                )
            )
    }

    /**
     * Redis 연결 상태 확인
     */
    fun checkRedisConnection(): Mono<Boolean> {
        //return redisTemplate.hasKey("health:ping")
        //    .timeout(Duration.ofSeconds(5))
        //    .onErrorReturn(false)
        return connectionFactory.reactiveConnection
            .ping()
            .map { pong -> pong == "PONG" }
            .timeout(Duration.ofSeconds(5))
            .onErrorReturn(false)
    }

    /**
     * 성능 메트릭 수집
     */
    fun collectPerformanceMetrics(): Mono<Map<String, Any>> {
        return Mono.fromCallable {
            val metrics = mutableMapOf<String, Any>()
            
            // Redis 기본 통계
            metrics["total_operations"] = performanceMetrics.values.sumOf { it.get() }
            metrics["average_latency"] = latencyMeasurements.values.average()
            metrics["max_latency"] = latencyMeasurements.values.maxOrNull() ?: 0L
            metrics["min_latency"] = latencyMeasurements.values.minOrNull() ?: 0L
            
            // 연결 통계
            metrics["connection_success_rate"] = calculateSuccessRate()
            metrics["total_connection_attempts"] = connectionErrors.get() + connectionSuccesses.get()
            
            // 메모리 사용량 (JVM)
            val runtime = Runtime.getRuntime()
            metrics["jvm_memory_used"] = runtime.totalMemory() - runtime.freeMemory()
            metrics["jvm_memory_free"] = runtime.freeMemory()
            metrics["jvm_memory_total"] = runtime.totalMemory()
            metrics["jvm_memory_max"] = runtime.maxMemory()
            
            metrics
        }
    }

    /**
     * 연결 성공률 계산
     */
    private fun calculateSuccessRate(): Double {
        val totalAttempts = connectionErrors.get() + connectionSuccesses.get()
        return if (totalAttempts > 0) {
            connectionSuccesses.get().toDouble() / totalAttempts * 100.0
        } else {
            0.0
        }
    }

    /**
     * 메트릭 증가
     */
    fun incrementMetric(metricName: String) {
        performanceMetrics.computeIfAbsent(metricName) { AtomicLong(0) }.incrementAndGet()
    }

    /**
     * 레이턴시 기록
     */
    fun recordLatency(operation: String, latencyMs: Long) {
        latencyMeasurements[operation] = latencyMs
    }

    /**
     * 전체 모니터링 대시보드 데이터
     */
    fun getMonitoringDashboard(): Mono<Map<String, Any>> {
        return Mono.zip(
            performHealthCheck(),
            collectPerformanceMetrics(),
            redisMessageBrokerService.healthCheck(),
            channelManagerService.healthCheck()
        ).map { tuple ->
            val health = tuple.t1
            val performance = tuple.t2
            val messageService = tuple.t3
            val channelManager = tuple.t4
            
            mapOf(
                "overall_status" to if (health["status"] == "UP") "HEALTHY" else "UNHEALTHY",
                "timestamp" to Instant.now().toString(),
                "health" to health,
                "performance" to performance,
                "services" to mapOf(
                    "message_service" to messageService,
                    "channel_manager" to channelManager
                )
            )
        }
    }

    /**
     * 알람 조건 체크
     */
    fun checkAlarmConditions(): Mono<List<String>> {
        return Mono.fromCallable {
            val alarms = mutableListOf<String>()
            
            // 연결 실패율이 높은 경우
            val failureRate = connectionErrors.get().toDouble() / 
                (connectionErrors.get() + connectionSuccesses.get()).coerceAtLeast(1) * 100
            if (failureRate > 10.0) {
                alarms.add("High Redis connection failure rate: ${String.format("%.2f", failureRate)}%")
            }
            
            // 평균 레이턴시가 높은 경우  
            val avgLatency = latencyMeasurements.values.average()
            if (avgLatency > 1000.0) {
                alarms.add("High Redis latency: ${String.format("%.2f", avgLatency)}ms")
            }
            
            // 메모리 사용률이 높은 경우
            val runtime = Runtime.getRuntime()
            val memoryUsagePercent = ((runtime.totalMemory() - runtime.freeMemory()).toDouble() / 
                runtime.maxMemory() * 100)
            if (memoryUsagePercent > 85.0) {
                alarms.add("High memory usage: ${String.format("%.2f", memoryUsagePercent)}%")
            }
            
            alarms
        }
    }

    /**
     * 통계 리셋
     */
    fun resetStatistics(): Mono<Void> {
        return Mono.fromRunnable {
            connectionErrors.set(0)
            connectionSuccesses.set(0)
            performanceMetrics.clear()
            latencyMeasurements.clear()
            logger.info("Redis monitoring statistics reset")
        }
    }
}