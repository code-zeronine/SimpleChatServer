package com.simplechat.domain.entity

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * 채팅 메시지 도메인 엔티티
 * 
 * 순수한 도메인 객체로 외부 프레임워크에 의존하지 않습니다.
 * 비즈니스 로직과 검증 규칙을 포함합니다.
 */
data class ChatMessage(
    val id: String? = null,
    
    @field:NotNull(message = "Room ID cannot be null")
    @field:Positive(message = "Room ID must be positive")
    val roomId: Long,
    
    @field:NotNull(message = "User ID cannot be null")
    @field:Positive(message = "User ID must be positive")
    val userId: Long,
    
    @field:NotBlank(message = "Content cannot be blank")
    @field:Size(max = 1000, message = "Content cannot exceed 1000 characters")
    val content: String,
    
    @field:NotNull(message = "Timestamp cannot be null")
    val timestamp: LocalDateTime = LocalDateTime.now(java.time.ZoneOffset.UTC),
    
    @field:NotNull(message = "Message type cannot be null")
    val messageType: MessageType = MessageType.TEXT
) {
    /**
     * 메시지가 유효한지 검증합니다.
     */
    fun isValid(): Boolean {
        return roomId > 0 && 
               userId > 0 && 
               content.isNotBlank() && 
               content.length <= 1000
    }
    
    /**
     * 시스템 메시지인지 확인합니다.
     */
    fun isSystemMessage(): Boolean {
        return messageType in listOf(MessageType.SYSTEM, MessageType.JOIN, MessageType.LEAVE)
    }
    
    /**
     * 사용자 메시지인지 확인합니다.
     */
    fun isUserMessage(): Boolean {
        return messageType == MessageType.TEXT
    }
    
    /**
     * 메시지의 간단한 정보를 반환합니다.
     */
    fun getMessageInfo(): String {
        return "Message(roomId=$roomId, userId=$userId, type=$messageType, timestamp=$timestamp)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChatMessage) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "ChatMessage(id=$id, roomId=$roomId, userId=$userId, messageType=$messageType, timestamp=$timestamp)"
    }
}
