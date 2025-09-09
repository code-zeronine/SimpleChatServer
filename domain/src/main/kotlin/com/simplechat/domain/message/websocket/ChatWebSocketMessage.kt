package com.simplechat.domain.message.websocket

data class ChatWebSocketMessage(
    override val type: WebSocketMessageType = WebSocketMessageType.CHAT,
    override val messageId: String?,
    override val timestamp: Long, // Unix timestamp in milliseconds
    override val sessionId: String?,
    val content: String,
    val userId: Long,
    val roomId: Long,
    val userNickname: String
) : WebSocketMessage()
