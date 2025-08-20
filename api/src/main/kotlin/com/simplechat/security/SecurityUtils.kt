package com.simplechat.security

import org.springframework.security.core.Authentication
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import reactor.core.publisher.Mono

object SecurityUtils {

    /**
     * 현재 인증된 사용자의 Authentication 객체를 반환합니다.
     */
    fun getCurrentAuthentication(): Mono<Authentication> {
        return ReactiveSecurityContextHolder.getContext()
            .map { it.authentication }
    }

    /**
     * 현재 인증된 사용자의 ID를 반환합니다.
     */
    fun getCurrentUserId(): Mono<String> {
        return getCurrentAuthentication()
            .map { auth -> auth.name }
            .switchIfEmpty(Mono.error(SecurityException("User not authenticated")))
    }

    /**
     * 현재 사용자가 특정 권한을 가지고 있는지 확인합니다.
     */
    fun hasAuthority(authority: String): Mono<Boolean> {
        return getCurrentAuthentication()
            .map { auth ->
                auth.authorities?.any { it.authority == authority } ?: false
            }
            .defaultIfEmpty(false)
    }

    /**
     * 현재 사용자가 인증되었는지 확인합니다.
     */
    fun isAuthenticated(): Mono<Boolean> {
        return getCurrentAuthentication()
            .map { auth -> auth.isAuthenticated }
            .defaultIfEmpty(false)
    }

    /**
     * JWT 토큰에서 Bearer 접두사를 제거합니다.
     */
    fun extractTokenFromHeader(authHeader: String?): String? {
        return authHeader?.takeIf { it.startsWith(SecurityConstants.JWT_PREFIX) }
            ?.substring(SecurityConstants.JWT_PREFIX.length)
    }

    /**
     * 요청 경로가 공개 엔드포인트인지 확인합니다.
     */
    fun isPublicEndpoint(path: String): Boolean {
        return SecurityConstants.PUBLIC_ENDPOINTS.any { publicPath ->
            path.startsWith(publicPath)
        }
    }
}