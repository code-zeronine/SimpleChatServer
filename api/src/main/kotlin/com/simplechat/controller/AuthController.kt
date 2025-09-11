package com.simplechat.controller

import com.simplechat.dto.auth.AuthResponse
import com.simplechat.dto.auth.LoginRequest
import com.simplechat.dto.auth.RefreshTokenRequest
import com.simplechat.dto.auth.RefreshTokenResponse
import com.simplechat.dto.auth.SignUpRequest
import com.simplechat.dto.common.ApiResponse
import com.simplechat.service.AuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

/**
 * 인증 관련 REST API 컨트롤러
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "인증 관리", description = "회원가입, 로그인, 토큰 관리 및 중복 확인 API")
class AuthController(
    private val authService: AuthService
) {

    /**
     * 회원가입 엔드포인트
     */
    @PostMapping("/signup")
    @Operation(
        summary = "회원가입",
        description = "새 사용자 계정을 생성합니다. 성공 시 JWT 액세스 토큰과 리프레시 토큰을 반환합니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "201", description = "회원가입 성공"),
            SwaggerApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
            SwaggerApiResponse(responseCode = "409", description = "이미 존재하는 이메일 또는 닉네임")
        ]
    )
    suspend fun signUp(
        @Parameter(description = "회원가입 요청 데이터", required = true)
        @Valid @RequestBody request: SignUpRequest
    ): ResponseEntity<ApiResponse<AuthResponse>> {
        val response = authService.signUp(request)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response, "회원가입이 성공적으로 완료되었습니다."))
    }

    /**
     * 로그인 엔드포인트
     */
    @PostMapping("/login")
    @Operation(
        summary = "로그인",
        description = "이메일과 비밀번호로 로그인합니다. 성공 시 JWT 액세스 토큰과 리프레시 토큰을 반환합니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "로그인 성공"),
            SwaggerApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
            SwaggerApiResponse(responseCode = "401", description = "인증 실패 (잘못된 이메일 또는 비밀번호)")
        ]
    )
    suspend fun login(
        @Parameter(description = "로그인 요청 데이터", required = true)
        @Valid @RequestBody request: LoginRequest
    ): ResponseEntity<ApiResponse<AuthResponse>> {
        val response = authService.login(request)
        return ResponseEntity.ok(ApiResponse.success(response, "로그인이 성공적으로 완료되었습니다."))
    }

    /**
     * 토큰 갱신 엔드포인트
     */
    @PostMapping("/refresh")
    @Operation(
        summary = "토큰 갱신",
        description = "리프레시 토큰을 사용하여 새로운 액세스 토큰을 발급받습니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "토큰 갱신 성공"),
            SwaggerApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
            SwaggerApiResponse(responseCode = "401", description = "유효하지 않은 리프레시 토큰")
        ]
    )
    suspend fun refreshToken(
        @Parameter(description = "토큰 갱신 요청 데이터", required = true)
        @Valid @RequestBody request: RefreshTokenRequest
    ): ResponseEntity<ApiResponse<RefreshTokenResponse>> {
        val response = authService.refreshToken(request)
        return ResponseEntity.ok(ApiResponse.success(response, "토큰이 성공적으로 갱신되었습니다."))
    }

    /**
     * 이메일 중복 확인 엔드포인트
     */
    @GetMapping("/check-email")
    @Operation(
        summary = "이메일 중복 확인",
        description = "주어진 이메일이 이미 사용 중인지 확인합니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "확인 성공"),
            SwaggerApiResponse(responseCode = "400", description = "잘못된 이메일 형식")
        ]
    )
    suspend fun checkEmailExists(
        @Parameter(description = "확인할 이메일 주소", required = true, example = "user@example.com")
        @RequestParam email: String
    ): ResponseEntity<ApiResponse<Map<String, Boolean>>> {
        val exists = authService.checkEmailExists(email)
        return ResponseEntity.ok(ApiResponse.success(mapOf("exists" to exists)))
    }

    /**
     * 닉네임 중복 확인 엔드포인트
     */
    @GetMapping("/check-nickname")
    @Operation(
        summary = "닉네임 중복 확인",
        description = "주어진 닉네임이 이미 사용 중인지 확인합니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "확인 성공"),
            SwaggerApiResponse(responseCode = "400", description = "잘못된 닉네임 형식")
        ]
    )
    suspend fun checkNicknameExists(
        @Parameter(description = "확인할 닉네임", required = true, example = "사용자123")
        @RequestParam nickname: String
    ): ResponseEntity<ApiResponse<Map<String, Boolean>>> {
        val exists = authService.checkNicknameExists(nickname)
        return ResponseEntity.ok(ApiResponse.success(mapOf("exists" to exists)))
    }

    /**
     * 로그아웃 엔드포인트
     */
    @PostMapping("/logout")
    @Operation(
        summary = "로그아웃",
        description = "현재 세션을 무효화하고 Redis에서 JWT 토큰 정보를 삭제합니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "로그아웃 성공"),
            SwaggerApiResponse(responseCode = "401", description = "인증되지 않은 요청")
        ]
    )
    suspend fun logout(
        @Parameter(description = "JWT 액세스 토큰", required = true)
        @RequestHeader(value = "Authorization", required = true) authHeader: String?
    ): ResponseEntity<ApiResponse<Map<String, String>>> {
        // Authorization 헤더에서 Bearer 토큰 추출
        val token = authHeader?.let { header ->
            when {
                header.startsWith("Bearer ", ignoreCase = true) -> header.substring(7).trim()
                header.isNotBlank() -> header.trim() // Bearer 없이 토큰만 있는 경우도 처리
                else -> null
            }
        }
        
        // 토큰이 없는 경우에도 로그아웃 성공으로 처리 (보안상 이유)
        if (token.isNullOrBlank()) {
            return ResponseEntity.ok(
                ApiResponse.success(
                    mapOf("message" to "로그아웃이 완료되었습니다. (토큰이 제공되지 않았거나 이미 만료되었을 수 있습니다.)")
                )
            )
        }
        
        val result = authService.logout(token)
        return ResponseEntity.ok(ApiResponse.success(mapOf("message" to result)))
    }

}