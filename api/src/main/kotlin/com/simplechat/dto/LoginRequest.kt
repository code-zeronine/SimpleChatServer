package com.simplechat.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

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
