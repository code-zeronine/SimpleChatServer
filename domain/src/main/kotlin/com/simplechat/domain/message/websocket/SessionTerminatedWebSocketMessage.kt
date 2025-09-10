package com.simplechat.domain.message.websocket

import java.time.Instant

/**
 * 세션 종료 WebSocket 메시지
 */
data class SessionTerminatedWebSocketMessage(
    override val messageId: String = "session-terminated-${System.currentTimeMillis()}",
    override val timestamp: Long = System.currentTimeMillis(),
    override val sessionId: String? = null,
    val reason: SessionTerminationReason,
    val message: String,
    val gracePeriodSeconds: Int = 5 // 연결 종료까지의 유예 시간 (초)
) : WebSocketMessage() {
    
    override val type: WebSocketMessageType = WebSocketMessageType.SESSION_TERMINATED
}

