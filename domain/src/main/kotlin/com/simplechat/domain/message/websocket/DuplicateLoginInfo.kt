package com.simplechat.domain.message.websocket

import java.time.Instant

/**
 * 중복 로그인 정보
 */
data class DuplicateLoginInfo(
    val loginTime: Instant,
    val ipAddress: String?,
    val userAgent: String?,
    val deviceInfo: String? = null,
    val location: String? = null
)