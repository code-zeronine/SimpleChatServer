package com.simplechat.dto

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
