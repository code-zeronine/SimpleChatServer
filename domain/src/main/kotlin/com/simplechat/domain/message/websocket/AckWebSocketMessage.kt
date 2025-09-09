package com.simplechat.domain.message.websocket

data class AckWebSocketMessage(
    override val type: WebSocketMessageType = WebSocketMessageType.ACK,
    override val messageId: String?,
    override val timestamp: Long, // Unix timestamp in milliseconds
    override val sessionId: String?,
    val originalMessageId: String,
    val status: String = "OK"
) : WebSocketMessage()
