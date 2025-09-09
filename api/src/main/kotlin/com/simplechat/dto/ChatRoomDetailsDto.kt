package com.simplechat.dto

/**
 * 채팅방 상세 정보 DTO (참여자 정보 포함)
 */
data class ChatRoomDetailsDto(
    val room: ChatRoomDto,
    val participants: List<ParticipantDto>,
    val admins: List<ParticipantDto>,
    val owner: ParticipantDto
)
