package com.simplechat.dto.chatroom

import com.simplechat.domain.entity.ChatRoom
import com.simplechat.dto.message.MessageDto
import java.time.format.DateTimeFormatter

/**
 * 채팅방 정보 DTO
 */
data class ChatRoomDto(
    val id: Long?,
    val name: String,
    val description: String?,
    val createdBy: Long,
    val isPrivate: Boolean,
    val maxParticipants: Int,
    val currentParticipants: Int,
    val createdAt: String,
    val updatedAt: String,
    val isJoined: Boolean,
    val latestMessage: MessageDto? = null // 마지막 메시지 정보 추가
) {
    companion object {
        private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

        fun from(
            chatRoom: ChatRoom,
            currentParticipants: Int = 0,
            isJoined: Boolean = false,
            latestMessage: MessageDto? = null
        ): ChatRoomDto {
            return ChatRoomDto(
                id = chatRoom.id,
                name = chatRoom.name,
                description = chatRoom.description,
                createdBy = chatRoom.createdBy,
                isPrivate = chatRoom.isPrivate,
                maxParticipants = chatRoom.maxParticipants,
                currentParticipants = currentParticipants,
                createdAt = chatRoom.createdAt.format(formatter),
                updatedAt = chatRoom.updatedAt.format(formatter),
                isJoined = isJoined,
                latestMessage = latestMessage
            )
        }
    }
}
