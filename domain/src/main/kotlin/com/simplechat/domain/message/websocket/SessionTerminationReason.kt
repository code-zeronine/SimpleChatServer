package com.simplechat.domain.message.websocket

/**
 * 세션 종료 이유
 */
enum class SessionTerminationReason {
    DUPLICATE_LOGIN,        // 중복 로그인으로 인한 종료
    FORCED_LOGOUT,          // 관리자에 의한 강제 로그아웃
    SECURITY_VIOLATION,     // 보안 위반
    INACTIVITY_TIMEOUT,     // 비활성화로 인한 타임아웃
    USER_REQUEST,           // 사용자 요청에 의한 종료
    SYSTEM_MAINTENANCE      // 시스템 점검
}