package com.simplechat.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

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
