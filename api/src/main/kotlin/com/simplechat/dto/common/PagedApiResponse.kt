package com.simplechat.dto.common

import java.time.Instant

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
