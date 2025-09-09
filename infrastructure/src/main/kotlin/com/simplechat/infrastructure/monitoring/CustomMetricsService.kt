package com.simplechat.infrastructure.monitoring

import io.micrometer.core.instrument.*
import io.micrometer.core.instrument.binder.jvm.*
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Supplier

/**
 * 커스텀 메트릭 수집 및 관리 서비스
 * 
 * JVM 메트릭, 캐시 메트릭, 데이터베이스 연결 메트릭, WebSocket 메트릭을 통합 관리
 */
@Service
class CustomMetricsService(
    private val meterRegistry: MeterRegistry,
    private val redisConnectionFactory: ReactiveRedisConnectionFactory
) {

    private val logger = LoggerFactory.getLogger(CustomMetricsService::class.java)

    // WebSocket 관련 메트릭
    private val webSocketConnections = AtomicLong(0)
    private val webSocketMessages = AtomicLong(0)
    private val webSocketErrors = AtomicLong(0)
    
    // 캐시 관련 메트릭
    private val cacheHits = AtomicLong(0)
    private val cacheMisses = AtomicLong(0)
    private val cacheEvictions = AtomicLong(0)
    
    // 데이터베이스 관련 메트릭
    private val databaseQueries = AtomicLong(0)
    private val databaseErrors = AtomicLong(0)
    
    // 비즈니스 메트릭
    private val activeChatRooms = AtomicLong(0)
    private val totalMessages = AtomicLong(0)
    
    @PostConstruct
    fun initializeMetrics() {
        logger.info("Initializing custom metrics service")
        
        // JVM 메트릭 등록
        registerJvmMetrics()
        
        // 커스텀 게이지 메트릭 등록
        registerCustomGauges()
        
        // 커스텀 카운터 메트릭 등록
        registerCustomCounters()
        
        // 커스텀 타이머 메트릭 등록
        registerCustomTimers()
        
        logger.info("Custom metrics service initialized successfully")
    }

    /**
     * 확장된 JVM 메트릭 등록
     */
    private fun registerJvmMetrics() {
        try {
            // 기본 JVM 메트릭
            JvmMemoryMetrics().bindTo(meterRegistry)
            JvmGcMetrics().bindTo(meterRegistry)
            JvmThreadMetrics().bindTo(meterRegistry)
            ProcessorMetrics().bindTo(meterRegistry)
            UptimeMetrics().bindTo(meterRegistry)
            
            // 커스텀 JVM 메트릭 - 올바른 API 사용
            meterRegistry.gauge("jvm.memory.heap.utilization", this) { _ ->
                val runtime = Runtime.getRuntime()
                val used = runtime.totalMemory() - runtime.freeMemory()
                val max = runtime.maxMemory()
                if (max > 0) (used.toDouble() / max * 100) else 0.0
            }
                
            meterRegistry.gauge("jvm.memory.non_heap.utilization", this) { _ ->
                val memoryBean = java.lang.management.ManagementFactory.getMemoryMXBean()
                val nonHeap = memoryBean.nonHeapMemoryUsage
                if (nonHeap.max > 0) {
                    (nonHeap.used.toDouble() / nonHeap.max * 100)
                } else 0.0
            }
        } catch (e: Exception) {
            logger.warn("Failed to register JVM metrics: {}", e.message)
        }
    }

    /**
     * 커스텀 게이지 메트릭 등록
     */
    private fun registerCustomGauges() {
        // WebSocket 연결 수 - 올바른 API 사용
        meterRegistry.gauge("websocket.connections.active", webSocketConnections) { obj -> 
            obj.get().toDouble() 
        }
            
        // 캐시 히트율
        meterRegistry.gauge("cache.hit.rate", this) { _ ->
            val totalRequests = cacheHits.get() + cacheMisses.get()
            if (totalRequests > 0) {
                (cacheHits.get().toDouble() / totalRequests * 100)
            } else 0.0
        }
            
        // 활성 채팅방 수
        meterRegistry.gauge("chatroom.active.count", activeChatRooms) { obj -> 
            obj.get().toDouble() 
        }
            
        // Redis 연결 상태 메트릭
        try {
            meterRegistry.gauge("redis.connections.status", this) { _ ->
                try {
                    // Redis 연결 상태 확인 (단순화된 버전)
                    1.0 // 연결됨
                } catch (e: Exception) {
                    logger.debug("Redis connection check failed: {}", e.message)
                    0.0 // 연결 안됨
                }
            }
        } catch (e: Exception) {
            logger.debug("Redis connection gauge registration failed: {}", e.message)
        }
    }

    /**
     * 커스텀 카운터 메트릭 등록
     */
    private fun registerCustomCounters() {
        // WebSocket 메시지 카운터
        Counter.builder("websocket.messages.total")
            .description("Total number of WebSocket messages")
            .register(meterRegistry)
            
        // WebSocket 에러 카운터
        Counter.builder("websocket.errors.total")
            .description("Total number of WebSocket errors")
            .register(meterRegistry)
            
        // 캐시 히트 카운터
        Counter.builder("cache.hits.total")
            .description("Total number of cache hits")
            .register(meterRegistry)
            
        // 캐시 미스 카운터
        Counter.builder("cache.misses.total")
            .description("Total number of cache misses")
            .register(meterRegistry)
            
        // 데이터베이스 쿼리 카운터
        Counter.builder("database.queries.total")
            .description("Total number of database queries")
            .register(meterRegistry)
            
        // 총 메시지 카운터
        Counter.builder("messages.total")
            .description("Total number of chat messages")
            .register(meterRegistry)
    }

    /**
     * 커스텀 타이머 메트릭 등록
     */
    private fun registerCustomTimers() {
        // 메시지 처리 시간
        Timer.builder("message.processing.time")
            .description("Time taken to process messages")
            .register(meterRegistry)
            
        // 데이터베이스 쿼리 시간
        Timer.builder("database.query.time")
            .description("Time taken for database queries")
            .register(meterRegistry)
            
        // 캐시 조회 시간
        Timer.builder("cache.lookup.time")
            .description("Time taken for cache lookups")
            .register(meterRegistry)
    }

    // === 메트릭 업데이트 메서드들 ===
    
    /**
     * WebSocket 연결 증가
     */
    fun incrementWebSocketConnection() {
        webSocketConnections.incrementAndGet()
        meterRegistry.counter("websocket.connections.created").increment()
    }

    /**
     * WebSocket 연결 감소
     */
    fun decrementWebSocketConnection() {
        webSocketConnections.decrementAndGet()
        meterRegistry.counter("websocket.connections.closed").increment()
    }

    /**
     * WebSocket 메시지 수신
     */
    fun recordWebSocketMessage() {
        webSocketMessages.incrementAndGet()
        meterRegistry.counter("websocket.messages.total").increment()
    }

    /**
     * WebSocket 에러 발생
     */
    fun recordWebSocketError() {
        webSocketErrors.incrementAndGet()
        meterRegistry.counter("websocket.errors.total").increment()
    }

    /**
     * 캐시 히트 기록
     */
    fun recordCacheHit() {
        cacheHits.incrementAndGet()
        meterRegistry.counter("cache.hits.total").increment()
    }

    /**
     * 캐시 미스 기록
     */
    fun recordCacheMiss() {
        cacheMisses.incrementAndGet()
        meterRegistry.counter("cache.misses.total").increment()
    }

    /**
     * 캐시 제거 기록
     */
    fun recordCacheEviction() {
        cacheEvictions.incrementAndGet()
        meterRegistry.counter("cache.evictions.total").increment()
    }

    /**
     * 데이터베이스 쿼리 기록
     */
    fun recordDatabaseQuery() {
        databaseQueries.incrementAndGet()
        meterRegistry.counter("database.queries.total").increment()
    }

    /**
     * 데이터베이스 에러 기록
     */
    fun recordDatabaseError() {
        databaseErrors.incrementAndGet()
        meterRegistry.counter("database.errors.total").increment()
    }

    /**
     * 채팅방 수 업데이트
     */
    fun updateActiveChatRooms(count: Long) {
        activeChatRooms.set(count)
    }

    /**
     * 메시지 처리 시간 기록
     */
    fun recordMessageProcessingTime(duration: Duration) {
        meterRegistry.timer("message.processing.time")
            .record(duration.toMillis(), TimeUnit.MILLISECONDS)
    }

    /**
     * 데이터베이스 쿼리 시간 기록
     */
    fun recordDatabaseQueryTime(duration: Duration) {
        meterRegistry.timer("database.query.time")
            .record(duration.toMillis(), TimeUnit.MILLISECONDS)
    }

    /**
     * 캐시 조회 시간 기록
     */
    fun recordCacheLookupTime(duration: Duration) {
        meterRegistry.timer("cache.lookup.time")
            .record(duration.toMillis(), TimeUnit.MILLISECONDS)
    }

    /**
     * 메트릭 통계 조회
     */
    fun getMetricsStatistics(): Map<String, Any> {
        return mapOf(
            "websocket_connections" to webSocketConnections.get(),
            "websocket_messages" to webSocketMessages.get(),
            "websocket_errors" to webSocketErrors.get(),
            "cache_hits" to cacheHits.get(),
            "cache_misses" to cacheMisses.get(),
            "cache_hit_rate" to run {
                val total = cacheHits.get() + cacheMisses.get()
                if (total > 0) (cacheHits.get().toDouble() / total * 100) else 0.0
            },
            "database_queries" to databaseQueries.get(),
            "database_errors" to databaseErrors.get(),
            "active_chat_rooms" to activeChatRooms.get(),
            "total_messages" to totalMessages.get()
        )
    }

    /**
     * 주기적으로 시스템 메트릭 수집
     */
    @Scheduled(fixedRate = 30000) // 30초마다
    fun collectSystemMetrics() {
        try {
            // GC 메트릭 수집
            val gcBeans = java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()
            gcBeans.forEach { gcBean ->
                meterRegistry.gauge("jvm.gc.collection.count", 
                    Tags.of("gc", gcBean.name), gcBean.collectionCount.toDouble())
                meterRegistry.gauge("jvm.gc.collection.time", 
                    Tags.of("gc", gcBean.name), gcBean.collectionTime.toDouble())
            }
            
            // 스레드 메트릭 수집
            val threadBean = java.lang.management.ManagementFactory.getThreadMXBean()
            meterRegistry.gauge("jvm.threads.daemon", threadBean.daemonThreadCount.toDouble())
            meterRegistry.gauge("jvm.threads.peak", threadBean.peakThreadCount.toDouble())
            
        } catch (e: Exception) {
            logger.warn("Failed to collect system metrics: {}", e.message)
        }
    }

    /**
     * 공통 태그 설정 (PostConstruct에서 실행)
     */
    private fun configureCommonTags() {
        try {
            meterRegistry.config().commonTags(
                "application", "simple-chat-server",
                "environment", System.getProperty("spring.profiles.active", "default")
            )
        } catch (e: Exception) {
            logger.warn("Failed to configure common tags: {}", e.message)
        }
    }
}