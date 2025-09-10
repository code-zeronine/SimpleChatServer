package com.simplechat.dto.session

import java.time.Instant

/**
 * 세션 세부 정보
 */
data class SessionDetail(
    val sessionId: String,
    val chatRoomId: String?,
    val connectedAt: Instant?,
    val lastActivityAt: Instant?,
    val messageCount: Int,
    val isActive: Boolean
)