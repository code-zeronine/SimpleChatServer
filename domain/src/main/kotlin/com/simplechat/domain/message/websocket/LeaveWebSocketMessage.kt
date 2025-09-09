package com.simplechat.domain.message.websocket

data class LeaveWebSocketMessage(
    override val type: WebSocketMessageType = WebSocketMessageType.LEAVE,
    override val messageId: String?,
    override val timestamp: Long, // Unix timestamp in milliseconds
    override val sessionId: String?,
    val userId: Long,
    val roomId: Long,
    val userNickname: String
) : WebSocketMessage()
