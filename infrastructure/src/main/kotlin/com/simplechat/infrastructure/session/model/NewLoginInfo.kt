package com.simplechat.infrastructure.session.model

import java.time.Instant

/**
 * 새로운 로그인 정보
 */
data class NewLoginInfo(
    val loginTime: Instant,
    val ipAddress: String?,
    val userAgent: String?
)