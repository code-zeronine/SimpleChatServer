package com.simplechat.infrastructure.monitoring

import io.micrometer.core.instrument.MeterRegistry
import io.r2dbc.pool.ConnectionPool
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.Instant
import java.util.function.Supplier

/**
 * 데이터베이스 연결 및 쿼리 메트릭 설정
 */
@Configuration
@AutoConfigureAfter(MetricsAutoConfiguration::class)
class DatabaseMetricsConfiguration {

    private val logger = LoggerFactory.getLogger(DatabaseMetricsConfiguration::class.java)

    @Autowired(required = false)
    private var connectionPool: ConnectionPool? = null

    @Autowired(required = false)
    private var meterRegistry: MeterRegistry? = null

    @Autowired(required = false)
    private var customMetricsService: CustomMetricsService? = null

    @PostConstruct
    fun configureDatabaseMetrics() {
        logger.info("Configuring database metrics")
        
        // R2DBC 메트릭 설정
        configureR2dbcMetrics()
        
        // MongoDB 메트릭 설정
        configureMongoMetrics()
        
        logger.info("Database metrics configuration completed")
    }

    /**
     * R2DBC 연결 풀 메트릭 설정
     */
    private fun configureR2dbcMetrics() {
        connectionPool?.let { pool ->
            meterRegistry?.let { registry ->
                try {
                    // 커스텀 R2DBC 메트릭
                    registry.gauge("r2dbc.connections.active", pool) { p -> 
                        p.metrics.map { it.acquiredSize().toDouble() }.orElse(0.0) 
                    }
                    
                    registry.gauge("r2dbc.connections.idle", pool) { p -> 
                        p.metrics.map { it.idleSize().toDouble() }.orElse(0.0) 
                    }
                    
                    registry.gauge("r2dbc.connections.pending", pool) { p -> 
                        p.metrics.map { it.pendingAcquireSize().toDouble() }.orElse(0.0) 
                    }
                    
                    registry.gauge("r2dbc.connections.max", pool) { p -> 
                        p.metrics.map { it.maxAllocatedSize.toDouble() }.orElse(0.0) 
                    }
                    
                    logger.info("R2DBC metrics configured successfully")
                } catch (e: Exception) {
                    logger.warn("Failed to configure R2DBC metrics: {}", e.message)
                }
            }
        } ?: run {
            logger.debug("R2DBC connection pool not available for metrics")
        }
    }

    /**
     * MongoDB 메트릭 설정
     */
    private fun configureMongoMetrics() {
        meterRegistry?.let { registry ->
            try {
                // MongoDB 연결 상태 메트릭
                registry.gauge("mongodb.connections.status", this) { _ ->
                    // MongoDB 연결 상태를 확인하는 로직
                    // 실제 구현에서는 MongoTemplate의 연결 상태를 확인
                    1.0 // 연결됨: 1, 연결 안됨: 0
                }
                
                logger.info("MongoDB metrics configured successfully")
            } catch (e: Exception) {
                logger.warn("Failed to configure MongoDB metrics: {}", e.message)
            }
        }
    }

    /**
     * 데이터베이스 쿼리 실행 시간을 측정하는 인터셉터
     */
    @Bean
    fun databaseQueryMetricsInterceptor(): DatabaseQueryMetricsInterceptor {
        return DatabaseQueryMetricsInterceptor(customMetricsService)
    }
}

/**
 * 데이터베이스 쿼리 메트릭 수집 인터셉터
 */
class DatabaseQueryMetricsInterceptor(
    private val customMetricsService: CustomMetricsService?
) {
    
    private val logger = LoggerFactory.getLogger(DatabaseQueryMetricsInterceptor::class.java)
    
    /**
     * 쿼리 실행 전후로 메트릭 수집
     */
    fun <T> measureQueryExecution(
        queryType: String,
        operation: () -> T
    ): T {
        val startTime = Instant.now()
        
        return try {
            customMetricsService?.recordDatabaseQuery()
            val result = operation()
            val duration = Duration.between(startTime, Instant.now())
            
            customMetricsService?.recordDatabaseQueryTime(duration)
            
            logger.debug("Database query executed: type={}, duration={}ms", 
                queryType, duration.toMillis())
            
            result
        } catch (e: Exception) {
            customMetricsService?.recordDatabaseError()
            logger.warn("Database query failed: type={}, error={}", queryType, e.message)
            throw e
        }
    }
    
    /**
     * Reactive 쿼리 실행 시간 측정을 위한 헬퍼 메서드
     */
    fun <T> measureReactiveQuery(
        queryType: String,
        publisher: reactor.core.publisher.Mono<T>
    ): reactor.core.publisher.Mono<T> {
        val startTime = Instant.now()
        
        return publisher
            .doOnSubscribe { 
                customMetricsService?.recordDatabaseQuery()
                logger.debug("Starting reactive database query: type={}", queryType)
            }
            .doOnSuccess { 
                val duration = Duration.between(startTime, Instant.now())
                customMetricsService?.recordDatabaseQueryTime(duration)
                logger.debug("Reactive database query completed: type={}, duration={}ms", 
                    queryType, duration.toMillis())
            }
            .doOnError { error ->
                customMetricsService?.recordDatabaseError()
                logger.warn("Reactive database query failed: type={}, error={}", 
                    queryType, error.message)
            }
    }
    
    /**
     * Reactive Flux 쿼리 실행 시간 측정
     */
    fun <T> measureReactiveQueryFlux(
        queryType: String,
        publisher: reactor.core.publisher.Flux<T>
    ): reactor.core.publisher.Flux<T> {
        val startTime = Instant.now()
        
        return publisher
            .doOnSubscribe { 
                customMetricsService?.recordDatabaseQuery()
                logger.debug("Starting reactive flux database query: type={}", queryType)
            }
            .doOnComplete { 
                val duration = Duration.between(startTime, Instant.now())
                customMetricsService?.recordDatabaseQueryTime(duration)
                logger.debug("Reactive flux database query completed: type={}, duration={}ms", 
                    queryType, duration.toMillis())
            }
            .doOnError { error ->
                customMetricsService?.recordDatabaseError()
                logger.warn("Reactive flux database query failed: type={}, error={}", 
                    queryType, error.message)
            }
    }
}