package com.simplechat.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * WebSocket 설정 프로퍼티
 */
@Component
@ConfigurationProperties(prefix = "websocket")
data class WebSocketProperties(
    var maxConnections: Int = 1000,
    var maxConnectionsPerUser: Int = 5,
    var handshakeTimeout: Duration = Duration.ofSeconds(30),
    var messageMaxSize: Int = 64 * 1024, // 64KB
    var heartbeatInterval: Duration = Duration.ofSeconds(30),
    var sessionTimeout: Duration = Duration.ofMinutes(30),
    var enableCompression: Boolean = true,
    var bufferSizeLimit: Int = 256 * 1024, // 256KB
    var allowedOrigins: List<String> = listOf("*"),
    var rateLimitPerSecond: Int = 100,
    var rateLimitBurstSize: Int = 200
)