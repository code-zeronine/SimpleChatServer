package com.simplechat.infrastructure.service

import java.time.Instant

/**
 * 세션 메타데이터 (캐싱 정보 포함)
 */
data class SessionMetadata(
    val sessionId: String,
    val userId: Long,
    val chatRoomId: String,
    val connectedAt: Instant,
    val lastActivityAt: Instant,
    val messageCount: Long,
    val lastMessageAt: Instant?,
    val status: String = "CONNECTED"
)
