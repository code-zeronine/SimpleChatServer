package com.simplechat.domain.service

/**
 * 메시지 라우팅 결과
 */
data class RoutingResult(
    val success: Boolean,
    val deliveredCount: Int,
    val failedCount: Int,
    val errors: List<String> = emptyList()
)
