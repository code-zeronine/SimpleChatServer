package com.simplechat.infrastructure.entity

import com.simplechat.domain.entity.ChatRoom
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

/**
 * ChatRoom 도메인 엔티티의 R2DBC 매핑을 위한 데이터베이스 엔티티
 * 
 * 데이터베이스 테이블과 도메인 모델 간의 매핑 역할을 수행합니다.
 */
@Table("chat_rooms")
data class ChatRoomEntity(
    @Id
    @Column("id")
    val id: Long? = null,
    
    @Column("name")
    val name: String,
    
    @Column("description")
    val description: String? = null,
    
    @Column("created_by")
    val createdBy: Long,
    
    @Column("is_private")
    val isPrivate: Boolean = false,
    
    @Column("max_participants")
    val maxParticipants: Int = 100,
    
    @Column("created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column("updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * ChatRoomEntity를 도메인 ChatRoom 객체로 변환합니다.
     */
    fun toDomain(): ChatRoom {
        return ChatRoom(
            id = this.id,
            name = this.name,
            description = this.description,
            createdBy = this.createdBy,
            isPrivate = this.isPrivate,
            maxParticipants = this.maxParticipants,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }
    
    companion object {
        /**
         * 도메인 ChatRoom 객체를 ChatRoomEntity로 변환합니다.
         */
        fun fromDomain(chatRoom: ChatRoom): ChatRoomEntity {
            return ChatRoomEntity(
                id = chatRoom.id,
                name = chatRoom.name,
                description = chatRoom.description,
                createdBy = chatRoom.createdBy,
                isPrivate = chatRoom.isPrivate,
                maxParticipants = chatRoom.maxParticipants,
                createdAt = chatRoom.createdAt,
                updatedAt = chatRoom.updatedAt
            )
        }
    }
}