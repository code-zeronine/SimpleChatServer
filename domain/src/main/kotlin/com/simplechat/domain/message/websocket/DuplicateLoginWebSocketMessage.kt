package com.simplechat.domain.message.websocket

import java.time.Instant

/**
 * 중복 로그인 감지 WebSocket 메시지
 */
data class DuplicateLoginWebSocketMessage(
    override val messageId: String = "duplicate-login-${System.currentTimeMillis()}",
    override val timestamp: Long = System.currentTimeMillis(),
    override val sessionId: String? = null,
    val message: String = "다른 위치에서 로그인이 감지되었습니다.",
    val newLoginInfo: DuplicateLoginInfo,
    val action: DuplicateLoginAction = DuplicateLoginAction.NOTIFY_ONLY
) : WebSocketMessage() {
    
    override val type: WebSocketMessageType = WebSocketMessageType.DUPLICATE_LOGIN_DETECTED
}

