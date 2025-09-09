package com.simplechat.security

import com.simplechat.domain.exception.auth.JwtAuthenticationException
import com.simplechat.infrastructure.security.jwt.JwtTokenProvider
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange

/**
 * JWT 인증을 도와주는 헬퍼 클래스 (Coroutine 방식)
 */
@Component
class JwtAuthenticationHelper(
    private val jwtTokenProvider: JwtTokenProvider
) {

    /**
     * ServerWebExchange에서 JWT 토큰을 추출하고 검증
     */
    suspend fun validateTokenFromExchange(exchange: ServerWebExchange): String {
        val token = extractTokenFromExchange(exchange)
        validateToken(token)
        return token
    }

    /**
     * Authorization 헤더에서 토큰을 추출
     */
    fun extractTokenFromHeader(authHeader: String?): String? {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null
        }
        return authHeader.substring(7) // "Bearer " 제거
    }

    /**
     * JWT 토큰 유효성 검증
     */
    fun validateToken(token: String): Boolean {
        return jwtTokenProvider.validateToken(token)
    }

    /**
     * 토큰에서 이메일 추출
     */
    fun getEmailFromToken(token: String): String {
        return try {
            jwtTokenProvider.getEmailFromToken(token)
        } catch (e: Exception) {
            throw JwtAuthenticationException("Failed to extract email from token: ${e.message}")
        }
    }

    /**
     * 토큰에서 사용자 ID 추출
     */
    fun getUserIdFromToken(token: String): Long {
        return try {
            jwtTokenProvider.getUserIdFromToken(token)
        } catch (e: Exception) {
            throw JwtAuthenticationException("Failed to extract userId from token: ${e.message}")
        }
    }

    /**
     * ServerWebExchange에서 Authorization 헤더 추출
     */
    private fun extractTokenFromExchange(exchange: ServerWebExchange): String {
        val authHeader = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
        return extractTokenFromHeader(authHeader)
            ?: throw JwtAuthenticationException("Authorization header is missing or invalid")
    }
}
