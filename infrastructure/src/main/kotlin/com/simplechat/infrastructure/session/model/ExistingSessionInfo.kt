package com.simplechat.infrastructure.session.model

import java.time.Instant

/**
 * 기존 세션 정보
 */
data class ExistingSessionInfo(
    val sessionId: String,
    val chatRoomId: String?,
    val connectedAt: Instant?,
    val lastActivityAt: Instant?
)