package com.simplechat.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * 중복 로그인 제어 설정
 */
@Configuration
@ConfigurationProperties(prefix = "simplechat.security.duplicate-login")
data class DuplicateLoginConfig(
    /**
     * 중복 로그인 제어 활성화 여부
     */
    var enabled: Boolean = true,

    /**
     * 중복 로그인 감지 시 동작 방식
     * - NOTIFY_ONLY: 알림만 전송
     * - FORCE_LOGOUT_OTHERS: 기존 세션들 강제 로그아웃
     * - DENY_NEW_LOGIN: 새로운 로그인 시도 거부
     * - ASK_USER_CHOICE: 사용자에게 선택권 제공
     */
    var action: DuplicateLoginConfigAction = DuplicateLoginConfigAction.NOTIFY_ONLY,

    /**
     * 최대 동시 세션 수 (0 = 무제한)
     */
    var maxConcurrentSessions: Int = 0,

    /**
     * 중복 로그인 알림 유효 기간 (초)
     */
    var notificationTimeoutSeconds: Int = 300,

    /**
     * 강제 로그아웃 시 유예 시간 (초)
     */
    var logoutGracePeriodSeconds: Int = 10,

    /**
     * IP 주소 기반 중복 로그인 허용 여부
     */
    var allowSameIpLogin: Boolean = true,

    /**
     * 관리자는 중복 로그인 제한 제외 여부
     */
    var excludeAdminUsers: Boolean = true,

    /**
     * 세션 만료 시간 (초) - 비활성 세션 정리용
     */
    var sessionTimeoutSeconds: Int = 1800,

    /**
     * 로그인 이력 보관 기간 (일)
     */
    var loginHistoryRetentionDays: Int = 30
)

