package com.simplechat.security

import com.simplechat.infrastructure.exception.JwtAuthenticationException
import com.simplechat.infrastructure.security.jwt.JwtTokenProvider
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

/**
 * JWT 인증을 도와주는 헬퍼 클래스
 */
@Component
class JwtAuthenticationHelper(
    private val jwtTokenProvider: JwtTokenProvider
) {

    /**
     * ServerWebExchange에서 JWT 토큰을 추출하고 검증
     */
    fun validateTokenFromExchange(exchange: ServerWebExchange): Mono<String> {
        return extractTokenFromExchange(exchange)
            .flatMap { token ->
                validateToken(token)
                    .map { token }
            }
    }

    /**
     * Authorization 헤더에서 토큰을 추출
     */
    fun extractTokenFromHeader(authHeader: String?): Mono<String> {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Mono.error(JwtAuthenticationException("Authorization header is missing or invalid"))
        }
        
        return Mono.just(authHeader.substring(7)) // "Bearer " 제거
    }

    /**
     * JWT 토큰 유효성 검증
     */
    fun validateToken(token: String): Mono<Void> {
        return Mono.fromCallable {
            if (!jwtTokenProvider.validateToken(token)) {
                throw JwtAuthenticationException("Invalid or expired token")
            }
        }.then()
    }

    /**
     * 토큰에서 이메일 추출
     */
    fun getEmailFromToken(token: String): Mono<String> {
        return Mono.fromCallable {
            jwtTokenProvider.getEmailFromToken(token)
        }.onErrorMap { e ->
            JwtAuthenticationException("Failed to extract email from token: ${e.message}")
        }
    }

    /**
     * 토큰에서 사용자 ID 추출
     */
    fun getUserIdFromToken(token: String): Mono<Long> {
        return Mono.fromCallable {
            jwtTokenProvider.getUserIdFromToken(token)
        }.onErrorMap { e ->
            JwtAuthenticationException("Failed to extract userId from token: ${e.message}")
        }
    }

    /**
     * ServerWebExchange에서 Authorization 헤더 추출
     */
    private fun extractTokenFromExchange(exchange: ServerWebExchange): Mono<String> {
        val authHeader = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
        return extractTokenFromHeader(authHeader)
    }
}
