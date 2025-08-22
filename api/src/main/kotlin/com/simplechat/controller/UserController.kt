package com.simplechat.controller

import com.simplechat.dto.UserDto
import com.simplechat.security.JwtAuthenticationHelper
import com.simplechat.service.AuthService
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

/**
 * 인증이 필요한 사용자 관련 API 컨트롤러
 */
@RestController
@RequestMapping("/api/users")
class UserController(
    private val authService: AuthService,
    private val jwtAuthenticationHelper: JwtAuthenticationHelper
) {

    /**
     * 현재 로그인한 사용자 정보 조회
     */
    @GetMapping("/me")
    fun getCurrentUser(@RequestHeader(HttpHeaders.AUTHORIZATION) authHeader: String): Mono<ResponseEntity<UserDto>> {
        return jwtAuthenticationHelper.extractTokenFromHeader(authHeader)
            .flatMap { token ->
                jwtAuthenticationHelper.validateToken(token)
                    .then(authService.validateUser(token))
            }
            .map { user ->
                ResponseEntity.ok(user)
            }
    }

    /**
     * 사용자 프로필 업데이트 (예시 엔드포인트)
     */
    @PutMapping("/profile")
    fun updateProfile(
        @RequestHeader(HttpHeaders.AUTHORIZATION) authHeader: String,
        @RequestBody updateRequest: Map<String, String>
    ): Mono<ResponseEntity<String>> {
        return jwtAuthenticationHelper.extractTokenFromHeader(authHeader)
            .flatMap { token ->
                jwtAuthenticationHelper.validateToken(token)
                    .then(jwtAuthenticationHelper.getUsernameFromToken(token))
            }
            .map { username ->
                // 실제 프로필 업데이트 로직은 추후 구현
                ResponseEntity.ok("Profile updated for user: $username")
            }
    }
}