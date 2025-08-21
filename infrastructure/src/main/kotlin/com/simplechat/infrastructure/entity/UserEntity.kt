package com.simplechat.infrastructure.entity

import com.simplechat.domain.entity.User
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

/**
 * User 도메인 엔티티의 R2DBC 매핑을 위한 데이터베이스 엔티티
 * 
 * 데이터베이스 테이블과 도메인 모델 간의 매핑 역할을 수행합니다.
 */
@Table("users")
data class UserEntity(
    @Id
    @Column("id")
    val id: Long? = null,
    
    @Column("email")
    val email: String,
    
    @Column("password_hash")
    val passwordHash: String,
    
    @Column("nickname")
    val nickname: String,
    
    @Column("created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * UserEntity를 도메인 User 객체로 변환합니다.
     */
    fun toDomain(): User {
        return User(
            id = this.id,
            email = this.email,
            passwordHash = this.passwordHash,
            nickname = this.nickname,
            createdAt = this.createdAt
        )
    }
    
    companion object {
        /**
         * 도메인 User 객체를 UserEntity로 변환합니다.
         */
        fun fromDomain(user: User): UserEntity {
            return UserEntity(
                id = user.id,
                email = user.email,
                passwordHash = user.passwordHash,
                nickname = user.nickname,
                createdAt = user.createdAt
            )
        }
    }
}