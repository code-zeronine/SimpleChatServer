package com.simplechat.dto.session

/**
 * 사용자 세션 정보
 */
data class UserSessionInfo(
    val userId: Long,
    val totalSessions: Int,
    val activeSessions: Int,
    val sessions: List<SessionDetail>
)