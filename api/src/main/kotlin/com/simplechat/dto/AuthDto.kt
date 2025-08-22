package com.simplechat.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * 인증 관련 DTO 클래스들
 */

/**
 * 회원가입 요청 DTO
 */
data class SignUpRequest(
    @field:Email(message = "유효한 이메일 주소를 입력해주세요.")
    @field:NotBlank(message = "이메일은 필수입니다.")
    val email: String,
    
    @field:NotBlank(message = "비밀번호는 필수입니다.")
    @field:Size(min = 8, max = 100, message = "비밀번호는 8자 이상 100자 이하여야 합니다.")
    val password: String,
    
    @field:NotBlank(message = "닉네임은 필수입니다.")
    @field:Size(min = 2, max = 50, message = "닉네임은 2자 이상 50자 이하여야 합니다.")
    val nickname: String
)

/**
 * 로그인 요청 DTO
 */
data class LoginRequest(
    @field:Email(message = "유효한 이메일 주소를 입력해주세요.")
    @field:NotBlank(message = "이메일은 필수입니다.")
    val email: String,
    
    @field:NotBlank(message = "비밀번호는 필수입니다.")
    val password: String
)

/**
 * 토큰 갱신 요청 DTO
 */
data class RefreshTokenRequest(
    @field:NotBlank(message = "리프레시 토큰은 필수입니다.")
    val refreshToken: String
)

/**
 * 인증 응답 DTO
 */
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val user: UserDto
)

/**
 * 사용자 DTO
 */
data class UserDto(
    val id: Long?,
    val email: String,
    val nickname: String,
    val createdAt: String
)

/**
 * 토큰 갱신 응답 DTO
 */
data class RefreshTokenResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long
)