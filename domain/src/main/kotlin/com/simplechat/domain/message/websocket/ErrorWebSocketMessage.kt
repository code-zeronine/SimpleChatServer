package com.simplechat.domain.message.websocket

data class ErrorWebSocketMessage(
    override val type: WebSocketMessageType = WebSocketMessageType.ERROR,
    override val messageId: String?,
    override val timestamp: Long, // Unix timestamp in milliseconds
    override val sessionId: String?,
    val errorCode: String,
    val errorMessage: String,
    val originalMessage: String? = null,
    val details: Map<String, Any>? = null
) : WebSocketMessage()
