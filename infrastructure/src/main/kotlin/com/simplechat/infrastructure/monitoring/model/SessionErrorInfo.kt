package com.simplechat.infrastructure.monitoring.model

import java.time.Instant

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
