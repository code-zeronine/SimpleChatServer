package com.simplechat.dto.participant

import com.simplechat.domain.entity.ChatRoomRole

/**
 * 참여자 역할 변경 요청 DTO
 */
data class ChangeParticipantRoleRequest(
    val targetUserId: Long,
    val newRole: ChatRoomRole
)
