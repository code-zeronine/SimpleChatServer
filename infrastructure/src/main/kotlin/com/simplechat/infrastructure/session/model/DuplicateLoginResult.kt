package com.simplechat.infrastructure.session.model

/**
 * 중복 로그인 감지 결과
 */
data class DuplicateLoginResult(
    val hasDuplicateLogin: Boolean,
    val existingSessionCount: Int,
    val existingSessions: List<ExistingSessionInfo>
)