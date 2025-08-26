package com.simplechat.dto

/**
 * API 응답을 위한 제네릭 래퍼 클래스
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val message: String?,
    val error: ApiError? = null
) {
    companion object {
        fun <T> success(data: T, message: String? = null): ApiResponse<T> {
            return ApiResponse(true, data, message, null)
        }

        fun <T> success(message: String? = null): ApiResponse<T> {
            return ApiResponse(true, null, message, null)
        }

        fun error(error: ApiError, message: String? = error.message): ApiResponse<Nothing> {
            return ApiResponse(false, null, message, error)
        }
    }
}

data class ApiError(
    val code: String,
    val message: String
)
