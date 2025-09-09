package com.simplechat.domain.message.websocket

data class SystemWebSocketMessage(
    override val type: WebSocketMessageType = WebSocketMessageType.SYSTEM,
    override val messageId: String?,
    override val timestamp: Long, // Unix timestamp in milliseconds
    override val sessionId: String?,
    val content: String,
    val level: String = "INFO",
    val userId: Long? = null, // 이벤트 발생 사용자 ID
    val userNickname: String? = null // 이벤트 발생 사용자 닉네임
) : WebSocketMessage()
