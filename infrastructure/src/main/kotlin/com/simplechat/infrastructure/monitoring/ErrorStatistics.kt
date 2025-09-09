package com.simplechat.infrastructure.monitoring

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
