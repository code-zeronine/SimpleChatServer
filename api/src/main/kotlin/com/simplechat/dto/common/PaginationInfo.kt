package com.simplechat.dto.common

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
