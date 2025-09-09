package com.simplechat.domain.service

/**
 * 메시지 라우팅 전략
 */
enum class RoutingStrategy {
    BROADCAST_TO_ROOM,      // 채팅방 내 모든 사용자
    SEND_TO_USER,           // 특정 사용자
    SEND_TO_USERS,          // 여러 사용자
    SEND_TO_SESSION,        // 특정 세션
    BROADCAST_GLOBALLY,     // 전역 브로드캐스트
    CUSTOM                  // 커스텀 라우팅
}
