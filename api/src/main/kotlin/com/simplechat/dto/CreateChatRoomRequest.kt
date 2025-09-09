package com.simplechat.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

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
