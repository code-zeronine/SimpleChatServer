package com.simplechat.dto

/**
 * 사용자 DTO
 */
data class UserDto(
    val id: Long?,
    val email: String,
    val nickname: String,
    val createdAt: String
)
