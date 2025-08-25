package com.simplechat.infrastructure.entity

import com.simplechat.domain.entity.ChatRoomRole
import com.simplechat.domain.entity.UserChatRoom
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.LocalDateTime

/**
 * UserChatRoom 도메인 엔티티의 R2DBC 매핑을 위한 데이터베이스 엔티티
 * 
 * 사용자와 채팅방 간의 다대다 관계를 나타내는 연결 테이블입니다.
 * 복합 키 (userId, chatRoomId)를 사용합니다.
 */
@Table("user_chat_rooms")
data class UserChatRoomEntity(
    @Column("user_id")
    val userId: Long,
    
    @Column("chat_room_id")
    val chatRoomId: Long,
    
    @Column("role")
    val role: String = ChatRoomRole.MEMBER.name,
    
    @Column("joined_at")
    val joinedAt: LocalDateTime = LocalDateTime.now(),
    
    @Column("is_active")
    val isActive: Boolean = true,
    
    @Column("last_read_at")
    val lastReadAt: LocalDateTime? = null,
    
    @Column("is_muted")
    val isMuted: Boolean = false,
    
    @Column("is_pinned")
    val isPinned: Boolean = false,
    
    @Column("left_at")
    val leftAt: LocalDateTime? = null,
    
    @Column("invited_by")
    val invitedBy: Long? = null,
    
    @Column("updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    /**
     * UserChatRoomEntity를 도메인 UserChatRoom 객체로 변환합니다.
     */
    fun toDomain(): UserChatRoom {
        return UserChatRoom(
            userId = this.userId,
            chatRoomId = this.chatRoomId,
            role = ChatRoomRole.fromString(this.role) ?: ChatRoomRole.MEMBER,
            joinedAt = this.joinedAt,
            isActive = this.isActive,
            lastReadAt = this.lastReadAt,
            isMuted = this.isMuted,
            isPinned = this.isPinned,
            leftAt = this.leftAt,
            invitedBy = this.invitedBy,
            updatedAt = this.updatedAt
        )
    }
    
    companion object {
        /**
         * 도메인 UserChatRoom 객체를 UserChatRoomEntity로 변환합니다.
         */
        fun fromDomain(userChatRoom: UserChatRoom): UserChatRoomEntity {
            return UserChatRoomEntity(
                userId = userChatRoom.userId,
                chatRoomId = userChatRoom.chatRoomId,
                role = userChatRoom.role.name,
                joinedAt = userChatRoom.joinedAt,
                isActive = userChatRoom.isActive,
                lastReadAt = userChatRoom.lastReadAt,
                isMuted = userChatRoom.isMuted,
                isPinned = userChatRoom.isPinned,
                leftAt = userChatRoom.leftAt,
                invitedBy = userChatRoom.invitedBy,
                updatedAt = userChatRoom.updatedAt
            )
        }
    }
}