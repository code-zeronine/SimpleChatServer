package com.simplechat.domain.message.websocket

/**
 * WebSocket 메시지 타입 열거형 (Domain)
 */
enum class WebSocketMessageType {
    CHAT,                      // 채팅 메시지
    JOIN,                      // 채팅방 입장
    LEAVE,                     // 채팅방 퇴장
    TYPING,                    // 타이핑 상태
    HEARTBEAT,                 // 하트비트
    SYSTEM,                    // 시스템 메시지
    ERROR,                     // 에러 메시지
    ACK,                       // 확인 메시지
    DUPLICATE_LOGIN_DETECTED,  // 중복 로그인 감지
    SESSION_TERMINATED         // 세션 강제 종료
}
