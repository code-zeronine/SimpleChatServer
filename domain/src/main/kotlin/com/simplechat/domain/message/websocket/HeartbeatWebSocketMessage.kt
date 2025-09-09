package com.simplechat.domain.message.websocket

data class HeartbeatWebSocketMessage(
    override val type: WebSocketMessageType = WebSocketMessageType.HEARTBEAT,
    override val messageId: String?,
    override val timestamp: Long, // Unix timestamp in milliseconds
    override val sessionId: String?
) : WebSocketMessage()
