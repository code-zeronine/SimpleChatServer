package com.simplechat.dto.chatroom

import jakarta.validation.constraints.Size

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
