package com.simplechat.domain.message.websocket

/**
 * 중복 로그인 시 취할 행동
 */
enum class DuplicateLoginAction {
    NOTIFY_ONLY,           // 알림만 전송
    FORCE_LOGOUT_OTHERS,   // 다른 세션들 강제 로그아웃
    FORCE_LOGOUT_CURRENT,  // 현재 로그인 시도 차단
    ASK_USER_CHOICE        // 사용자에게 선택권 제공
}