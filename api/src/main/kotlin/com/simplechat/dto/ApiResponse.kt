package com.simplechat.dto

import java.time.Instant

/**
 * API 응답을 위한 통합된 제네릭 래퍼 클래스
 * 
 * 모든 REST API 응답에 대한 일관된 구조를 제공합니다.
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val error: ApiError? = null,
    val timestamp: String = Instant.now().toString(),
    val path: String? = null,
    val statusCode: Int? = null
) {
    companion object {
        /**
         * 데이터가 포함된 성공 응답을 생성합니다.
         */
        fun <T> success(data: T, message: String? = null): ApiResponse<T> {
            return ApiResponse(
                success = true,
                data = data,
                message = message
            )
        }

        /**
         * 데이터가 없는 성공 응답을 생성합니다.
         */
        fun success(message: String? = null): ApiResponse<Unit> {
            return ApiResponse(
                success = true,
                data = Unit,
                message = message
            )
        }

        /**
         * 에러 응답을 생성합니다.
         */
        fun <T> error(
            code: String, 
            message: String, 
            path: String? = null,
            statusCode: Int? = null
        ): ApiResponse<T> {
            return ApiResponse(
                success = false,
                data = null,
                message = message,
                error = ApiError(code, message),
                path = path,
                statusCode = statusCode
            )
        }

        /**
         * ApiError 객체를 사용한 에러 응답을 생성합니다.
         */
        fun <T> error(
            error: ApiError, 
            message: String? = error.message,
            path: String? = null,
            statusCode: Int? = null
        ): ApiResponse<T> {
            return ApiResponse(
                success = false,
                data = null,
                message = message,
                error = error,
                path = path,
                statusCode = statusCode
            )
        }
    }
}

