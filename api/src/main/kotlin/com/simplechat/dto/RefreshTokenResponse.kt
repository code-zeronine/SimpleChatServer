package com.simplechat.dto

/**
 * 토큰 갱신 응답 DTO
 */
data class RefreshTokenResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long
)
