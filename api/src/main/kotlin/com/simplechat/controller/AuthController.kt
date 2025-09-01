package com.simplechat.controller

import com.simplechat.dto.AuthResponse
import com.simplechat.dto.LoginRequest
import com.simplechat.dto.RefreshTokenRequest
import com.simplechat.dto.RefreshTokenResponse
import com.simplechat.dto.SignUpRequest
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
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
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
    fun signUp(
        @Parameter(description = "회원가입 요청 데이터", required = true)
        @Valid @RequestBody request: SignUpRequest
    ): Mono<ResponseEntity<AuthResponse>> {
        return authService.signUp(request)
            .map { response ->
                ResponseEntity.status(HttpStatus.CREATED).body(response)
            }
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
    fun login(
        @Parameter(description = "로그인 요청 데이터", required = true)
        @Valid @RequestBody request: LoginRequest
    ): Mono<ResponseEntity<AuthResponse>> {
        return authService.login(request)
            .map { response ->
                ResponseEntity.ok(response)
            }
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
    fun refreshToken(
        @Parameter(description = "토큰 갱신 요청 데이터", required = true)
        @Valid @RequestBody request: RefreshTokenRequest
    ): Mono<ResponseEntity<RefreshTokenResponse>> {
        return authService.refreshToken(request)
            .map { response ->
                ResponseEntity.ok(response)
            }
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
    fun checkEmailExists(
        @Parameter(description = "확인할 이메일 주소", required = true, example = "user@example.com")
        @RequestParam email: String
    ): Mono<ResponseEntity<Map<String, Boolean>>> {
        return authService.checkEmailExists(email)
            .map { exists ->
                ResponseEntity.ok(mapOf("exists" to exists))
            }
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
    fun checkNicknameExists(
        @Parameter(description = "확인할 닉네임", required = true, example = "사용자123")
        @RequestParam nickname: String
    ): Mono<ResponseEntity<Map<String, Boolean>>> {
        return authService.checkNicknameExists(nickname)
            .map { exists ->
                ResponseEntity.ok(mapOf("exists" to exists))
            }
    }

}