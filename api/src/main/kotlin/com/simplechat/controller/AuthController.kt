package com.simplechat.controller

import com.simplechat.dto.*
import com.simplechat.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

/**
 * 인증 관련 REST API 컨트롤러
 */
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {

    /**
     * 회원가입 엔드포인트
     */
    @PostMapping("/signup")
    fun signUp(@Valid @RequestBody request: SignUpRequest): Mono<ResponseEntity<AuthResponse>> {
        return authService.signUp(request)
            .map { response ->
                ResponseEntity.status(HttpStatus.CREATED).body(response)
            }
    }

    /**
     * 로그인 엔드포인트
     */
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): Mono<ResponseEntity<AuthResponse>> {
        return authService.login(request)
            .map { response ->
                ResponseEntity.ok(response)
            }
    }

    /**
     * 토큰 갱신 엔드포인트
     */
    @PostMapping("/refresh")
    fun refreshToken(@Valid @RequestBody request: RefreshTokenRequest): Mono<ResponseEntity<RefreshTokenResponse>> {
        return authService.refreshToken(request)
            .map { response ->
                ResponseEntity.ok(response)
            }
    }

    /**
     * 이메일 중복 확인 엔드포인트
     */
    @GetMapping("/check-email")
    fun checkEmailExists(@RequestParam email: String): Mono<ResponseEntity<Map<String, Boolean>>> {
        return authService.checkEmailExists(email)
            .map { exists ->
                ResponseEntity.ok(mapOf("exists" to exists))
            }
    }

    /**
     * 닉네임 중복 확인 엔드포인트
     */
    @GetMapping("/check-nickname")
    fun checkNicknameExists(@RequestParam nickname: String): Mono<ResponseEntity<Map<String, Boolean>>> {
        return authService.checkNicknameExists(nickname)
            .map { exists ->
                ResponseEntity.ok(mapOf("exists" to exists))
            }
    }

    /**
     * 사용자 정보 조회 엔드포인트 (토큰 기반)
     */
    @GetMapping("/me")
    fun getCurrentUser(@RequestHeader("Authorization") authHeader: String): Mono<ResponseEntity<UserDto>> {
        val token = authHeader.removePrefix("Bearer ")
        return authService.validateUser(token)
            .map { user ->
                ResponseEntity.ok(user)
            }
    }
}