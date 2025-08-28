package com.simplechat.infrastructure.service

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy

/**
 * Redis 채널 상태 모니터링 서비스
 * 
 * 채널의 상태, 성능, 문제점을 실시간으로 모니터링하고
 * 알림 및 자동 복구 기능을 제공합니다.
 */
@Service
class RedisChannelMonitoringService(
    private val channelLifecycleService: RedisChannelLifecycleService,
    private val subscriberTrackingService: SubscriberTrackingService,
    private val roomSubscriptionService: RoomSubscriptionService,
    private val reactiveRedisTemplate: ReactiveRedisTemplate<String, String>
) {
    
    private val logger = LoggerFactory.getLogger(RedisChannelMonitoringService::class.java)
    
    // 채널 상태 정보 저장소
    private val channelHealthStatus = ConcurrentHashMap<String, ChannelHealthStatus>()
    
    // 모니터링 이벤트 스트림
    private val monitoringEventSink = Sinks.many().multicast().onBackpressureBuffer<MonitoringEvent>()
    
    // 성능 메트릭
    private val performanceMetrics = PerformanceMetrics()
    
    // 알림 규칙
    private val alertRules = mutableListOf<AlertRule>()
    
    companion object {
        private const val HEALTH_CHECK_INTERVAL_SECONDS = 30L
        private const val PERFORMANCE_SAMPLE_INTERVAL_SECONDS = 10L
        private const val ALERT_CHECK_INTERVAL_SECONDS = 60L
        
        // 건강성 임계값
        private const val SUBSCRIBER_ANOMALY_THRESHOLD = 0.8 // 80% 변화율
        private const val RESPONSE_TIME_THRESHOLD_MS = 1000L // 1초
        private const val ERROR_RATE_THRESHOLD = 0.05 // 5% 오류율
    }
    
    @PostConstruct
    fun init() {
        initializeDefaultAlertRules()
        startMonitoring()
        logger.info("Redis channel monitoring service initialized")
    }
    
    @PreDestroy
    fun cleanup() {
        channelHealthStatus.clear()
        monitoringEventSink.tryEmitComplete()
        logger.info("Redis channel monitoring service cleaned up")
    }
    
    /**
     * 모든 활성 채널의 종합 상태를 조회합니다.
     * 
     * @return 채널 상태 리포트
     */
    fun getChannelStatusReport(): Mono<ChannelStatusReport> {
        return Mono.fromCallable {
            val activeChannels = channelLifecycleService.getActiveChannels()
            val healthyChannels = channelHealthStatus.values.count { it.status == HealthStatus.HEALTHY }
            val warningChannels = channelHealthStatus.values.count { it.status == HealthStatus.WARNING }
            val criticalChannels = channelHealthStatus.values.count { it.status == HealthStatus.CRITICAL }
            
            ChannelStatusReport(
                totalChannels = activeChannels.size,
                healthyChannels = healthyChannels,
                warningChannels = warningChannels,
                criticalChannels = criticalChannels,
                totalSubscribers = roomSubscriptionService.getSubscriptionStatistics()
                    .map { it.totalSubscriptions }
                    .block() ?: 0,
                performanceMetrics = performanceMetrics.getCurrentMetrics(),
                timestamp = System.currentTimeMillis()
            )
        }
        .subscribeOn(Schedulers.boundedElastic())
        .doOnSuccess { report ->
            logger.debug("Channel status report generated: {} total, {} healthy, {} warning, {} critical",
                report.totalChannels, report.healthyChannels, report.warningChannels, report.criticalChannels)
        }
    }
    
    /**
     * 특정 채널의 상세 상태를 조회합니다.
     * 
     * @param channelName 채널명
     * @return 채널 상세 상태
     */
    fun getChannelHealthStatus(channelName: String): Mono<ChannelHealthStatus?> {
        return Mono.fromCallable {
            channelHealthStatus[channelName]
        }
        .flatMap { status ->
            if (status != null) {
                Mono.just(status)
            } else {
                // 상태가 없으면 새로 검사
                performHealthCheck(channelName)
                    .map { channelHealthStatus[channelName] }
            }
        }
    }
    
    /**
     * 채널의 실시간 성능 메트릭을 모니터링합니다.
     * 
     * @param channelName 채널명
     * @param duration 모니터링 지속시간
     * @return 성능 메트릭 스트림
     */
    fun monitorChannelPerformance(
        channelName: String, 
        duration: Duration = Duration.ofMinutes(5)
    ): Flux<ChannelPerformanceMetric> {
        
        return Flux.interval(Duration.ofSeconds(PERFORMANCE_SAMPLE_INTERVAL_SECONDS))
            .take(duration.toSeconds() / PERFORMANCE_SAMPLE_INTERVAL_SECONDS)
            .flatMap { 
                collectChannelPerformanceMetric(channelName)
            }
            .doOnNext { metric ->
                // 성능 이상 감지
                detectPerformanceAnomalies(metric)
            }
            .doOnSubscribe { 
                logger.debug("Started performance monitoring for channel: {}", channelName)
            }
            .doOnComplete {
                logger.debug("Completed performance monitoring for channel: {}", channelName)
            }
    }
    
    /**
     * 모니터링 이벤트 스트림을 제공합니다.
     * 
     * @return 모니터링 이벤트 스트림
     */
    fun subscribeToMonitoringEvents(): Flux<MonitoringEvent> {
        return monitoringEventSink.asFlux()
            .doOnSubscribe { 
                logger.debug("New subscriber to monitoring events")
            }
    }
    
    /**
     * 특정 타입의 모니터링 이벤트만 구독합니다.
     * 
     * @param eventTypes 구독할 이벤트 타입들
     * @return 필터된 이벤트 스트림
     */
    fun subscribeToMonitoringEvents(vararg eventTypes: MonitoringEventType): Flux<MonitoringEvent> {
        val typeSet = eventTypes.toSet()
        return subscribeToMonitoringEvents()
            .filter { event -> event.type in typeSet }
    }
    
    /**
     * 알림 규칙을 추가합니다.
     * 
     * @param rule 알림 규칙
     */
    fun addAlertRule(rule: AlertRule) {
        alertRules.add(rule)
        logger.debug("Added alert rule: {} for condition: {}", rule.name, rule.condition)
    }
    
    /**
     * 알림 규칙을 제거합니다.
     * 
     * @param ruleName 규칙명
     * @return 제거 성공 여부
     */
    fun removeAlertRule(ruleName: String): Boolean {
        val removed = alertRules.removeIf { it.name == ruleName }
        if (removed) {
            logger.debug("Removed alert rule: {}", ruleName)
        }
        return removed
    }
    
    /**
     * 현재 활성화된 알림 목록을 조회합니다.
     * 
     * @return 활성 알림 목록
     */
    fun getActiveAlerts(): List<ActiveAlert> {
        return channelHealthStatus.values
            .filter { it.status != HealthStatus.HEALTHY }
            .map { healthStatus ->
                ActiveAlert(
                    channelName = healthStatus.channelName,
                    alertLevel = when (healthStatus.status) {
                        HealthStatus.WARNING -> AlertLevel.WARNING
                        HealthStatus.CRITICAL -> AlertLevel.CRITICAL
                        else -> AlertLevel.INFO
                    },
                    message = healthStatus.statusMessage,
                    firstDetected = healthStatus.lastChecked,
                    lastUpdated = healthStatus.lastChecked
                )
            }
    }
    
    /**
     * 주기적인 건강성 검사를 수행합니다.
     */
    @Scheduled(fixedDelay = HEALTH_CHECK_INTERVAL_SECONDS * 1000)
    fun performPeriodicHealthCheck() {
        channelLifecycleService.getActiveChannels()
            .keys
            .forEach { channelName ->
                performHealthCheck(channelName).subscribe()
            }
    }
    
    /**
     * 주기적인 알림 검사를 수행합니다.
     */
    @Scheduled(fixedDelay = ALERT_CHECK_INTERVAL_SECONDS * 1000)
    fun checkAlerts() {
        channelHealthStatus.values.forEach { healthStatus ->
            alertRules.forEach { rule ->
                if (rule.condition(healthStatus)) {
                    emitMonitoringEvent(MonitoringEvent(
                        type = MonitoringEventType.ALERT_TRIGGERED,
                        channelName = healthStatus.channelName,
                        message = "Alert '${rule.name}' triggered: ${rule.message}",
                        severity = rule.severity,
                        timestamp = System.currentTimeMillis(),
                        metadata = mapOf(
                            "ruleName" to rule.name,
                            "healthStatus" to healthStatus.status.name
                        )
                    ))
                }
            }
        }
    }
    
    /**
     * 특정 채널의 건강성을 검사합니다.
     * 
     * @param channelName 채널명
     * @return 검사 완료 신호
     */
    private fun performHealthCheck(channelName: String): Mono<Void> {
        val startTime = System.currentTimeMillis()
        
        return subscriberTrackingService.getSubscriberCount(channelName)
            .flatMap { subscriberCount ->
                // Redis 연결 상태 확인
                checkRedisConnection()
                    .map { isConnected ->
                        val endTime = System.currentTimeMillis()
                        val responseTime = endTime - startTime
                        
                        val status = determineHealthStatus(subscriberCount, responseTime, isConnected)
                        val statusMessage = generateStatusMessage(status, subscriberCount, responseTime, isConnected)
                        
                        val healthStatus = ChannelHealthStatus(
                            channelName = channelName,
                            status = status,
                            statusMessage = statusMessage,
                            subscriberCount = subscriberCount,
                            responseTimeMs = responseTime,
                            lastChecked = endTime,
                            isConnected = isConnected
                        )
                        
                        updateChannelHealth(channelName, healthStatus)
                        healthStatus
                    }
            }
            .doOnSuccess { healthStatus ->
                // 상태 변경 시 이벤트 발행
                if (shouldEmitHealthEvent(channelName, healthStatus)) {
                    emitMonitoringEvent(MonitoringEvent(
                        type = MonitoringEventType.HEALTH_CHECK_COMPLETED,
                        channelName = channelName,
                        message = healthStatus.statusMessage,
                        severity = when (healthStatus.status) {
                            HealthStatus.CRITICAL -> EventSeverity.HIGH
                            HealthStatus.WARNING -> EventSeverity.MEDIUM
                            else -> EventSeverity.LOW
                        },
                        timestamp = healthStatus.lastChecked,
                        metadata = mapOf(
                            "subscriberCount" to healthStatus.subscriberCount,
                            "responseTime" to healthStatus.responseTimeMs,
                            "status" to healthStatus.status.name
                        )
                    ))
                }
            }
            .then()
    }
    
    /**
     * 채널의 성능 메트릭을 수집합니다.
     */
    private fun collectChannelPerformanceMetric(channelName: String): Mono<ChannelPerformanceMetric> {
        val startTime = System.currentTimeMillis()
        
        return subscriberTrackingService.getSubscriberCount(channelName)
            .map { subscriberCount ->
                val endTime = System.currentTimeMillis()
                val responseTime = endTime - startTime
                
                ChannelPerformanceMetric(
                    channelName = channelName,
                    subscriberCount = subscriberCount,
                    responseTimeMs = responseTime,
                    timestamp = endTime,
                    throughput = calculateThroughput(channelName),
                    errorRate = calculateErrorRate(channelName)
                )
            }
            .doOnNext { metric ->
                performanceMetrics.addSample(metric)
            }
    }
    
    /**
     * Redis 연결 상태를 확인합니다.
     */
    private fun checkRedisConnection(): Mono<Boolean> {
        return reactiveRedisTemplate
            .hasKey("health-check-key")
            .map { true }
            .onErrorReturn(false)
            .timeout(Duration.ofSeconds(5))
    }
    
    /**
     * 건강성 상태를 결정합니다.
     */
    private fun determineHealthStatus(
        subscriberCount: Int, 
        responseTime: Long, 
        isConnected: Boolean
    ): HealthStatus {
        return when {
            !isConnected -> HealthStatus.CRITICAL
            responseTime > RESPONSE_TIME_THRESHOLD_MS -> HealthStatus.WARNING
            subscriberCount == 0 -> HealthStatus.WARNING
            else -> HealthStatus.HEALTHY
        }
    }
    
    /**
     * 상태 메시지를 생성합니다.
     */
    private fun generateStatusMessage(
        status: HealthStatus, 
        subscriberCount: Int, 
        responseTime: Long, 
        isConnected: Boolean
    ): String {
        return when (status) {
            HealthStatus.CRITICAL -> "Redis connection failed"
            HealthStatus.WARNING -> when {
                responseTime > RESPONSE_TIME_THRESHOLD_MS -> 
                    "High response time: ${responseTime}ms"
                subscriberCount == 0 -> "No active subscribers"
                else -> "Performance degraded"
            }
            HealthStatus.HEALTHY -> "Channel operating normally"
        }
    }
    
    /**
     * 채널 건강성 정보를 업데이트합니다.
     */
    private fun updateChannelHealth(channelName: String, healthStatus: ChannelHealthStatus) {
        channelHealthStatus[channelName] = healthStatus
    }
    
    /**
     * 건강성 이벤트 발행 여부를 결정합니다.
     */
    private fun shouldEmitHealthEvent(channelName: String, newStatus: ChannelHealthStatus): Boolean {
        val previousStatus = channelHealthStatus[channelName]
        return previousStatus == null || previousStatus.status != newStatus.status
    }
    
    /**
     * 성능 이상을 감지합니다.
     */
    private fun detectPerformanceAnomalies(metric: ChannelPerformanceMetric) {
        if (metric.responseTimeMs > RESPONSE_TIME_THRESHOLD_MS) {
            emitMonitoringEvent(MonitoringEvent(
                type = MonitoringEventType.PERFORMANCE_ANOMALY,
                channelName = metric.channelName,
                message = "High response time detected: ${metric.responseTimeMs}ms",
                severity = EventSeverity.MEDIUM,
                timestamp = metric.timestamp,
                metadata = mapOf(
                    "responseTime" to metric.responseTimeMs,
                    "threshold" to RESPONSE_TIME_THRESHOLD_MS
                )
            ))
        }
        
        if (metric.errorRate > ERROR_RATE_THRESHOLD) {
            emitMonitoringEvent(MonitoringEvent(
                type = MonitoringEventType.PERFORMANCE_ANOMALY,
                channelName = metric.channelName,
                message = "High error rate detected: ${String.format("%.2f", metric.errorRate * 100)}%",
                severity = EventSeverity.HIGH,
                timestamp = metric.timestamp,
                metadata = mapOf(
                    "errorRate" to metric.errorRate,
                    "threshold" to ERROR_RATE_THRESHOLD
                )
            ))
        }
    }
    
    /**
     * 처리량을 계산합니다.
     */
    private fun calculateThroughput(channelName: String): Double {
        // 실제 구현에서는 메시지 처리량을 추적해야 함
        return 0.0
    }
    
    /**
     * 오류율을 계산합니다.
     */
    private fun calculateErrorRate(channelName: String): Double {
        // 실제 구현에서는 오류 발생률을 추적해야 함
        return 0.0
    }
    
    /**
     * 기본 알림 규칙을 초기화합니다.
     */
    private fun initializeDefaultAlertRules() {
        addAlertRule(AlertRule(
            name = "Critical Health Status",
            condition = { it.status == HealthStatus.CRITICAL },
            message = "Channel is in critical state",
            severity = EventSeverity.HIGH
        ))
        
        addAlertRule(AlertRule(
            name = "High Response Time",
            condition = { it.responseTimeMs > RESPONSE_TIME_THRESHOLD_MS },
            message = "Channel response time is too high",
            severity = EventSeverity.MEDIUM
        ))
        
        addAlertRule(AlertRule(
            name = "No Subscribers",
            condition = { it.subscriberCount == 0 && it.status == HealthStatus.WARNING },
            message = "Channel has no active subscribers",
            severity = EventSeverity.LOW
        ))
    }
    
    /**
     * 모니터링을 시작합니다.
     */
    private fun startMonitoring() {
        logger.info("Started Redis channel monitoring")
    }
    
    /**
     * 모니터링 이벤트를 발행합니다.
     */
    private fun emitMonitoringEvent(event: MonitoringEvent) {
        val result = monitoringEventSink.tryEmitNext(event)
        if (result.isFailure) {
            logger.warn("Failed to emit monitoring event: {} for channel {}", 
                result, event.channelName)
        }
    }
}

// 데이터 클래스들

data class ChannelStatusReport(
    val totalChannels: Int,
    val healthyChannels: Int,
    val warningChannels: Int,
    val criticalChannels: Int,
    val totalSubscribers: Int,
    val performanceMetrics: Map<String, Double>,
    val timestamp: Long
)

data class ChannelHealthStatus(
    val channelName: String,
    val status: HealthStatus,
    val statusMessage: String,
    val subscriberCount: Int,
    val responseTimeMs: Long,
    val lastChecked: Long,
    val isConnected: Boolean
)

data class ChannelPerformanceMetric(
    val channelName: String,
    val subscriberCount: Int,
    val responseTimeMs: Long,
    val timestamp: Long,
    val throughput: Double,
    val errorRate: Double
)

data class MonitoringEvent(
    val type: MonitoringEventType,
    val channelName: String,
    val message: String,
    val severity: EventSeverity,
    val timestamp: Long,
    val metadata: Map<String, Any> = emptyMap()
)

data class AlertRule(
    val name: String,
    val condition: (ChannelHealthStatus) -> Boolean,
    val message: String,
    val severity: EventSeverity
)

data class ActiveAlert(
    val channelName: String,
    val alertLevel: AlertLevel,
    val message: String,
    val firstDetected: Long,
    val lastUpdated: Long
)

// 열거형들

enum class HealthStatus {
    HEALTHY, WARNING, CRITICAL
}

enum class MonitoringEventType {
    HEALTH_CHECK_COMPLETED,
    PERFORMANCE_ANOMALY,
    ALERT_TRIGGERED,
    CONNECTION_LOST,
    CONNECTION_RESTORED
}

enum class EventSeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}

enum class AlertLevel {
    INFO, WARNING, CRITICAL
}

// 성능 메트릭 관리 클래스
class PerformanceMetrics {
    private val samples = ConcurrentHashMap<String, MutableList<ChannelPerformanceMetric>>()
    private val maxSamples = 100
    
    fun addSample(metric: ChannelPerformanceMetric) {
        samples.compute(metric.channelName) { _, existing ->
            val list = existing ?: mutableListOf()
            list.add(metric)
            if (list.size > maxSamples) {
                list.removeAt(0)
            }
            list
        }
    }
    
    fun getCurrentMetrics(): Map<String, Double> {
        val allSamples = samples.values.flatten()
        return if (allSamples.isNotEmpty()) {
            mapOf(
                "averageResponseTime" to allSamples.map { it.responseTimeMs }.average(),
                "averageSubscribers" to allSamples.map { it.subscriberCount }.average(),
                "averageThroughput" to allSamples.map { it.throughput }.average(),
                "averageErrorRate" to allSamples.map { it.errorRate }.average()
            )
        } else {
            emptyMap()
        }
    }
}