package com.simplechat.dto.participant

/**
 * 참여자 추방 요청 DTO
 */
data class KickParticipantRequest(
    val targetUserId: Long,
    val reason: String? = null
)
