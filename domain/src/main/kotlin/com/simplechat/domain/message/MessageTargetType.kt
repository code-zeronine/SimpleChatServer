package com.simplechat.domain.message

/**
 * 메시지 전송 대상 타입
 */
enum class MessageTargetType {
    USER,       // 특정 사용자들에게
    ROOM,       // 특정 채팅방에
    SESSION,    // 특정 세션에게
    GLOBAL      // 전체 사용자에게
}
