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

/**
 * API 에러 정보를 담는 데이터 클래스
 */
data class ApiError(
    val code: String,
    val message: String,
    val details: Map<String, Any?>? = null
)

/**
 * 페이지네이션을 지원하는 API 응답 클래스
 */
data class PagedApiResponse<T>(
    val success: Boolean,
    val data: List<T>? = null,
    val pagination: PaginationInfo? = null,
    val message: String? = null,
    val error: ApiError? = null,
    val timestamp: String = Instant.now().toString(),
    val path: String? = null,
    val statusCode: Int? = null
) {
    companion object {
        /**
         * 페이지네이션된 성공 응답을 생성합니다.
         */
        fun <T> success(
            data: List<T>,
            pagination: PaginationInfo,
            message: String? = null
        ): PagedApiResponse<T> {
            return PagedApiResponse(
                success = true,
                data = data,
                pagination = pagination,
                message = message
            )
        }

        /**
         * 페이지네이션된 에러 응답을 생성합니다.
         */
        fun <T> error(
            code: String,
            message: String,
            path: String? = null,
            statusCode: Int? = null
        ): PagedApiResponse<T> {
            return PagedApiResponse(
                success = false,
                data = null,
                message = message,
                error = ApiError(code, message),
                path = path,
                statusCode = statusCode
            )
        }
    }
}

/**
 * 페이지네이션 정보를 담는 데이터 클래스
 */
data class PaginationInfo(
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)
