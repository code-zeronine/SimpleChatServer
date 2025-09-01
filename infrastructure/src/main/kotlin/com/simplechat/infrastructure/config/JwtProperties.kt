package com.simplechat.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * JWT 설정 프로퍼티
 */
@Component
@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    var secret: String = "",
    var expiration: Long = 86400000L, // 24 hours in milliseconds
    var refreshExpiration: Long = 604800000L, // 7 days in milliseconds
    var issuer: String = "simple-chat-server",
    var header: String = "Authorization",
    var prefix: String = "Bearer"
)