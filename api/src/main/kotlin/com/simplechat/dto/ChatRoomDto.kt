package com.simplechat.dto

import com.simplechat.domain.entity.ChatRoom
import com.simplechat.domain.entity.ChatRoomRole
import com.simplechat.domain.entity.UserChatRoom
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.format.DateTimeFormatter

/**
 * 채팅방 관련 DTO 클래스들
 */

/**
 * 채팅방 생성 요청 DTO
 */
data class CreateChatRoomRequest(
    @field:NotBlank(message = "채팅방 이름은 필수입니다.")
    @field:Size(min = 1, max = 100, message = "채팅방 이름은 1자 이상 100자 이하여야 합니다.")
    val name: String,
    
    @field:Size(max = 500, message = "채팅방 설명은 500자 이하여야 합니다.")
    val description: String? = null,
    
    val isPrivate: Boolean = false,
    
    val maxParticipants: Int = 100
)

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
    val isJoined: Boolean
) {
    companion object {
        private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        
        fun from(chatRoom: ChatRoom, currentParticipants: Int = 0, isJoined: Boolean = false): ChatRoomDto {
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
                isJoined = isJoined
            )
        }
    }
}

/**
 * 채팅방 상세 정보 DTO (참여자 정보 포함)
 */
data class ChatRoomDetailsDto(
    val room: ChatRoomDto,
    val participants: List<ParticipantDto>,
    val admins: List<ParticipantDto>,
    val owner: ParticipantDto
)

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

/**
 * 채팅방 참여 요청 DTO
 */
data class JoinChatRoomRequest(
    val inviteCode: String? = null
)

/**
 * 채팅방 목록 조회 응답 DTO
 */
data class ChatRoomListResponse(
    val rooms: List<ChatRoomDto>,
    val totalCount: Int,
    val page: Int,
    val size: Int,
    val hasNext: Boolean
)

/**
 * 채팅방 업데이트 요청 DTO
 */
data class UpdateChatRoomRequest(
    @field:Size(min = 1, max = 100, message = "채팅방 이름은 1자 이상 100자 이하여야 합니다.")
    val name: String? = null,
    
    @field:Size(max = 500, message = "채팅방 설명은 500자 이하여야 합니다.")
    val description: String? = null,
    
    val maxParticipants: Int? = null
)

/**
 * 참여자 역할 변경 요청 DTO
 */
data class ChangeParticipantRoleRequest(
    val targetUserId: Long,
    val newRole: ChatRoomRole
)

/**
 * 참여자 추방 요청 DTO
 */
data class KickParticipantRequest(
    val targetUserId: Long,
    val reason: String? = null
)
