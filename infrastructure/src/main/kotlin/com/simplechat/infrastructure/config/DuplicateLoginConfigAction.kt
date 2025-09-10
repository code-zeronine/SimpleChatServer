package com.simplechat.infrastructure.config

/**
 * 중복 로그인 시 취할 행동
 */
enum class DuplicateLoginConfigAction {
    NOTIFY_ONLY,           // 알림만 전송
    FORCE_LOGOUT_OTHERS,   // 기존 세션들 강제 로그아웃
    DENY_NEW_LOGIN,        // 새로운 로그인 시도 거부
    ASK_USER_CHOICE        // 사용자에게 선택권 제공
}