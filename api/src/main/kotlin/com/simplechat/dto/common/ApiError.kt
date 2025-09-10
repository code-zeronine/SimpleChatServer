package com.simplechat.dto.common

/**
 * API 에러 정보를 담는 데이터 클래스
 */
data class ApiError(
    val code: String,
    val message: String,
    val details: Map<String, Any?>? = null
)
