package com.simplechat.dto

/**
 * 참여자 추방 요청 DTO
 */
data class KickParticipantRequest(
    val targetUserId: Long,
    val reason: String? = null
)
