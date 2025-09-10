package com.simplechat.infrastructure.monitoring.model

import com.simplechat.domain.exception.websocket.WebSocketErrorCode
import java.time.Instant

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
    fun addError(errorCode: WebSocketErrorCode, @Suppress("UNUSED_PARAMETER") severity: ErrorSeverity) {
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
