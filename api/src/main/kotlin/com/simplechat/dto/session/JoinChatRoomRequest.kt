package com.simplechat.dto.session

/**
 * 채팅방 참여 요청 DTO
 */
data class JoinChatRoomRequest(
    val inviteCode: String? = null
)