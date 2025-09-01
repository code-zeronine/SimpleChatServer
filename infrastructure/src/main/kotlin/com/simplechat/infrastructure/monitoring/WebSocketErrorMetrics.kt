package com.simplechat.infrastructure.monitoring

import com.simplechat.domain.exception.WebSocketErrorCode
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder

/**
 * WebSocket 에러 메트릭스를 수집하고 관리하는 클래스
 * 
 * 에러 발생 통계, 세션별 에러 추적, 에러 패턴 분석 등을 제공합니다.
 */
@Component
class WebSocketErrorMetrics {
    
    // 에러 타입별 발생 카운트
    private val errorCountsByCode = ConcurrentHashMap<String, LongAdder>()
    
    // 세션별 에러 발생 카운트
    private val errorCountsBySession = ConcurrentHashMap<String, LongAdder>()
    
    // 시간대별 에러 발생 통계 (최근 24시간)
    private val hourlyErrorCounts = ConcurrentHashMap<Int, LongAdder>()
    
    // 전체 에러 발생 카운트
    private val totalErrors = LongAdder()
    
    // 연속된 에러 발생 감지를 위한 세션 추적
    private val sessionErrorSequence = ConcurrentHashMap<String, ErrorSequence>()
    
    // 에러 발생 시간 추적 (최근 1000개)
    private val recentErrorTimes = mutableListOf<Instant>()
    private val maxRecentErrors = 1000
    
    /**
     * 에러 발생을 기록합니다.
     */
    fun recordError(
        sessionId: String,
        errorCode: WebSocketErrorCode,
        severity: ErrorSeverity = ErrorSeverity.ERROR
    ) {
        synchronized(this) {
            // 전체 에러 카운트 증가
            totalErrors.increment()
            
            // 에러 코드별 카운트 증가
            errorCountsByCode.computeIfAbsent(errorCode.code) { LongAdder() }.increment()
            
            // 세션별 에러 카운트 증가
            errorCountsBySession.computeIfAbsent(sessionId) { LongAdder() }.increment()
            
            // 시간대별 에러 카운트 증가
            val currentHour = Instant.now().epochSecond / 3600
            hourlyErrorCounts.computeIfAbsent((currentHour % 24).toInt()) { LongAdder() }.increment()
            
            // 연속 에러 추적
            updateSessionErrorSequence(sessionId, errorCode, severity)
            
            // 최근 에러 시간 기록
            updateRecentErrorTimes()
        }
    }
    
    /**
     * 세션의 연속 에러 발생 패턴을 업데이트합니다.
     */
    private fun updateSessionErrorSequence(
        sessionId: String,
        errorCode: WebSocketErrorCode,
        severity: ErrorSeverity
    ) {
        val sequence = sessionErrorSequence.computeIfAbsent(sessionId) { 
            ErrorSequence(sessionId) 
        }
        
        sequence.addError(errorCode, severity)
        
        // 연속 에러가 임계치를 넘으면 경고 로그
        if (sequence.consecutiveErrors >= 5) {
            // 여기서 알림이나 추가 처리를 할 수 있음
            println("WARNING: Session $sessionId has ${sequence.consecutiveErrors} consecutive errors")
        }
    }
    
    /**
     * 최근 에러 발생 시간을 업데이트합니다.
     */
    private fun updateRecentErrorTimes() {
        val now = Instant.now()
        recentErrorTimes.add(now)
        
        // 최대 개수 초과 시 오래된 것 제거
        if (recentErrorTimes.size > maxRecentErrors) {
            recentErrorTimes.removeAt(0)
        }
    }
    
    /**
     * 에러 통계를 조회합니다.
     */
    fun getErrorStatistics(): ErrorStatistics {
        val now = Instant.now()
        val oneHourAgo = now.minusSeconds(3600)
        val oneMinuteAgo = now.minusSeconds(60)
        
        // 최근 1시간 내 에러 수
        val errorsInLastHour = recentErrorTimes.count { it.isAfter(oneHourAgo) }
        
        // 최근 1분 내 에러 수
        val errorsInLastMinute = recentErrorTimes.count { it.isAfter(oneMinuteAgo) }
        
        // 가장 많이 발생한 에러 TOP 5
        val topErrors = errorCountsByCode.entries
            .sortedByDescending { it.value.sum() }
            .take(5)
            .associate { it.key to it.value.sum() }
        
        // 에러가 많이 발생한 세션 TOP 5
        val problemSessions = errorCountsBySession.entries
            .sortedByDescending { it.value.sum() }
            .take(5)
            .associate { it.key to it.value.sum() }
        
        return ErrorStatistics(
            totalErrors = totalErrors.sum(),
            errorsInLastHour = errorsInLastHour,
            errorsInLastMinute = errorsInLastMinute,
            errorsByCode = topErrors,
            problemSessions = problemSessions,
            activeSessionsWithErrors = sessionErrorSequence.size
        )
    }
    
    /**
     * 특정 세션의 에러 정보를 조회합니다.
     */
    fun getSessionErrorInfo(sessionId: String): SessionErrorInfo? {
        val errorCount = errorCountsBySession[sessionId]?.sum() ?: return null
        val sequence = sessionErrorSequence[sessionId]
        
        return SessionErrorInfo(
            sessionId = sessionId,
            totalErrors = errorCount,
            consecutiveErrors = sequence?.consecutiveErrors ?: 0,
            lastErrorTime = sequence?.lastErrorTime,
            mostFrequentError = sequence?.mostFrequentError
        )
    }
    
    /**
     * 에러율을 계산합니다.
     */
    fun calculateErrorRate(timeWindowMinutes: Int = 5): Double {
        val windowStart = Instant.now().minusSeconds(timeWindowMinutes * 60L)
        val errorsInWindow = recentErrorTimes.count { it.isAfter(windowStart) }
        
        // 분당 에러율 계산
        return errorsInWindow.toDouble() / timeWindowMinutes
    }
    
    /**
     * 세션 정리 (세션 종료 시 호출)
     */
    fun cleanupSession(sessionId: String) {
        errorCountsBySession.remove(sessionId)
        sessionErrorSequence.remove(sessionId)
    }
    
    /**
     * 시간대별 에러 통계 초기화 (매일 자정에 호출)
     */
    fun resetDailyStatistics() {
        hourlyErrorCounts.clear()
        
        // 오래된 에러 기록 정리 (7일 이상 된 것)
        val sevenDaysAgo = Instant.now().minusSeconds(7 * 24 * 3600)
        recentErrorTimes.removeIf { it.isBefore(sevenDaysAgo) }
    }
    
    /**
     * 알람이 필요한 상황인지 확인합니다.
     */
    fun shouldTriggerAlarm(): AlarmStatus {
        val errorRate = calculateErrorRate(5) // 최근 5분간 에러율
        val errorsInLastMinute = recentErrorTimes.count { 
            it.isAfter(Instant.now().minusSeconds(60)) 
        }
        
        return when {
            errorRate > 10.0 -> AlarmStatus.CRITICAL // 분당 10개 이상
            errorRate > 5.0 -> AlarmStatus.WARNING   // 분당 5개 이상
            errorsInLastMinute > 20 -> AlarmStatus.WARNING // 최근 1분간 20개 이상
            else -> AlarmStatus.NORMAL
        }
    }
}

/**
 * 에러 심각도
 */
enum class ErrorSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

/**
 * 알람 상태
 */
enum class AlarmStatus {
    NORMAL,
    WARNING,
    CRITICAL
}

/**
 * 세션별 에러 연속 발생 추적
 */
data class ErrorSequence(
    val sessionId: String,
    var consecutiveErrors: Int = 0,
    var lastErrorTime: Instant? = null,
    var mostFrequentError: String? = null,
    private val errorHistory: MutableList<String> = mutableListOf()
) {
    fun addError(errorCode: WebSocketErrorCode, severity: ErrorSeverity) {
        consecutiveErrors++
        lastErrorTime = Instant.now()
        errorHistory.add(errorCode.code)
        
        // 가장 빈번한 에러 업데이트
        mostFrequentError = errorHistory.groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }?.key
        
        // 연속 에러 기록은 최대 100개까지만 유지
        if (errorHistory.size > 100) {
            errorHistory.removeAt(0)
        }
    }
    
    fun resetConsecutiveErrors() {
        consecutiveErrors = 0
    }
}

/**
 * 전체 에러 통계
 */
data class ErrorStatistics(
    val totalErrors: Long,
    val errorsInLastHour: Int,
    val errorsInLastMinute: Int,
    val errorsByCode: Map<String, Long>,
    val problemSessions: Map<String, Long>,
    val activeSessionsWithErrors: Int
)

/**
 * 세션별 에러 정보
 */
data class SessionErrorInfo(
    val sessionId: String,
    val totalErrors: Long,
    val consecutiveErrors: Int,
    val lastErrorTime: Instant?,
    val mostFrequentError: String?
)