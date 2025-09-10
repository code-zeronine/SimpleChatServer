package com.simplechat.infrastructure.session.model

import java.time.Instant

/**
 * WebSocket 세션 메타데이터
 */
data class SessionMetadata(
    val sessionId: String,
    val userId: Long,
    val chatRoomId: String,
    val connectedAt: Instant,
    val lastActivityAt: Instant,
    val messageCount: Int = 0,
    val lastMessageAt: Instant? = null,
    val status: String = "ACTIVE",
    val userAgent: String? = null,
    val ipAddress: String? = null
)

