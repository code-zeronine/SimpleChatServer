package com.simplechat.dto

import com.simplechat.domain.entity.ChatRoomRole
import com.simplechat.domain.entity.UserChatRoom
import java.time.format.DateTimeFormatter

/**
 * 참여자 정보 DTO
 */
data class ParticipantDto(
    val userId: Long,
    val nickname: String,
    val role: ChatRoomRole,
    val joinedAt: String,
    val lastReadAt: String?,
    val isActive: Boolean,
    val isMuted: Boolean,
    val isPinned: Boolean,
    val isOnline: Boolean
) {
    companion object {
        private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        
        fun from(userChatRoom: UserChatRoom, userNickname: String, isOnline: Boolean): ParticipantDto {
            return ParticipantDto(
                userId = userChatRoom.userId,
                nickname = userNickname,
                role = userChatRoom.role,
                joinedAt = userChatRoom.joinedAt.format(formatter),
                lastReadAt = userChatRoom.lastReadAt?.format(formatter),
                isActive = userChatRoom.isActive,
                isMuted = userChatRoom.isMuted,
                isPinned = userChatRoom.isPinned,
                isOnline = isOnline
            )
        }
    }
}
