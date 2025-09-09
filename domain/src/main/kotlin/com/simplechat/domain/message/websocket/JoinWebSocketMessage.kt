package com.simplechat.domain.message.websocket

data class JoinWebSocketMessage(
    override val type: WebSocketMessageType = WebSocketMessageType.JOIN,
    override val messageId: String?,
    override val timestamp: Long, // Unix timestamp in milliseconds
    override val sessionId: String?,
    val userId: Long,
    val roomId: Long,
    val userNickname: String
) : WebSocketMessage()
