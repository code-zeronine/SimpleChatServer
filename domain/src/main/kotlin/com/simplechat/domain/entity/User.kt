package com.simplechat.domain.entity

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * 사용자 도메인 엔티티
 * 
 * 순수한 도메인 객체로 외부 프레임워크에 의존하지 않습니다.
 * 비즈니스 로직과 검증 규칙을 포함합니다.
 */
data class User(
    val id: Long? = null,
    
    @field:Email(message = "유효한 이메일 주소를 입력해주세요.")
    @field:NotBlank(message = "이메일은 필수입니다.")
    val email: String,
    
    @field:NotBlank(message = "비밀번호는 필수입니다.")
    val passwordHash: String,
    
    @field:NotBlank(message = "닉네임은 필수입니다.")
    @field:Size(min = 2, max = 50, message = "닉네임은 2자 이상 50자 이하여야 합니다.")
    val nickname: String,
    
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * 사용자가 유효한지 검증합니다.
     */
    fun isValid(): Boolean {
        return email.isNotBlank() && 
               passwordHash.isNotBlank() && 
               nickname.length in 2..50 &&
               email.contains("@")
    }
    
    /**
     * 비밀번호가 해시된 비밀번호와 일치하는지 확인합니다.
     */
    fun isPasswordValid(hashedPassword: String): Boolean {
        return this.passwordHash == hashedPassword
    }
    
    /**
     * 사용자의 표시명을 반환합니다.
     */
    fun getDisplayName(): String {
        return nickname.ifBlank { email.substringBefore("@") }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is User) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "User(id=$id, email='$email', nickname='$nickname', createdAt=$createdAt)"
    }
}