package com.simplechat.domain.entity

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Table("users")
data class User(
    @Id
    @Column("id")
    val id: Long? = null,
    
    @field:Email(message = "유효한 이메일 주소를 입력해주세요")
    @field:NotBlank(message = "이메일은 필수 입력 항목입니다")
    @Column("email")
    val email: String,
    
    @field:NotBlank(message = "비밀번호 해시는 필수 입력 항목입니다")
    @Column("password_hash")
    val passwordHash: String,
    
    @field:NotBlank(message = "닉네임은 필수 입력 항목입니다")
    @field:Size(min = 2, max = 50, message = "닉네임은 2자 이상 50자 이하여야 합니다")
    @Column("nickname")
    val nickname: String,
    
    @Column("created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    override fun toString(): String {
        return "User(id=$id, email='$email', nickname='$nickname', createdAt=$createdAt)"
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as User
        
        return id != null && id == other.id
    }
    
    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }
}