package com.simplechat.domain.constants

/**
 * 채널 타입 열거형
 */
enum class ChannelType {
    CHAT_ROOM,      // 채팅방 채널
    USER_PRIVATE,   // 사용자 개인 채널
    GLOBAL,         // 글로벌 채널
    SYSTEM,         // 시스템 채널
    ADMIN,          // 관리자 채널
    CUSTOM          // 커스텀 채널
}
