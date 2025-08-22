package com.simplechat.domain.entity

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * 채팅방 도메인 엔티티
 * 
 * 순수한 도메인 객체로 외부 프레임워크에 의존하지 않습니다.
 * 채팅방의 핵심 비즈니스 로직과 검증 규칙을 포함합니다.
 */
data class ChatRoom(
    val id: Long? = null,
    
    @field:NotBlank(message = "채팅방 이름은 필수입니다.")
    @field:Size(min = 1, max = 100, message = "채팅방 이름은 1자 이상 100자 이하여야 합니다.")
    val name: String,
    
    @field:Size(max = 500, message = "채팅방 설명은 500자 이하여야 합니다.")
    val description: String? = null,
    
    val createdBy: Long, // 채팅방 생성자 사용자 ID
    
    val isPrivate: Boolean = false, // 비공개 채팅방 여부
    
    val maxParticipants: Int = 100, // 최대 참여자 수
    
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    
    /**
     * 채팅방이 유효한지 검증합니다.
     */
    fun isValid(): Boolean {
        return name.isNotBlank() && 
               name.length <= 100 &&
               (description?.length ?: 0) <= 500 &&
               maxParticipants > 0 &&
               maxParticipants <= 1000 // 시스템 제한
    }
    
    /**
     * 사용자가 이 채팅방의 소유자인지 확인합니다.
     */
    fun isOwnedBy(userId: Long): Boolean {
        return this.createdBy == userId
    }
    
    /**
     * 채팅방이 비공개 채팅방인지 확인합니다.
     */
    fun isPrivateRoom(): Boolean {
        return this.isPrivate
    }
    
    /**
     * 채팅방이 참여자 수 제한에 도달했는지 확인합니다.
     */
    fun isAtCapacity(currentParticipants: Int): Boolean {
        return currentParticipants >= maxParticipants
    }
    
    /**
     * 채팅방 표시명을 반환합니다.
     */
    fun getDisplayName(): String {
        return name.trim().ifBlank { "이름 없는 채팅방" }
    }
    
    /**
     * 채팅방 정보를 업데이트합니다.
     */
    fun updateInfo(
        newName: String? = null,
        newDescription: String? = null,
        newMaxParticipants: Int? = null
    ): ChatRoom {
        return this.copy(
            name = newName?.takeIf { it.isNotBlank() } ?: this.name,
            description = newDescription ?: this.description,
            maxParticipants = newMaxParticipants?.takeIf { it > 0 && it <= 1000 } ?: this.maxParticipants,
            updatedAt = LocalDateTime.now()
        )
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChatRoom) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "ChatRoom(id=$id, name='$name', createdBy=$createdBy, isPrivate=$isPrivate, createdAt=$createdAt)"
    }
}