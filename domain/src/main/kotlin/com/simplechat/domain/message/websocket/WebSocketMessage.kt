package com.simplechat.domain.message.websocket

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * WebSocket 메시지 기본 인터페이스 (Domain)
 * 
 * 모든 WebSocket 메시지의 기본 구조를 정의합니다.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ChatWebSocketMessage::class, name = "CHAT"),
    JsonSubTypes.Type(value = JoinWebSocketMessage::class, name = "JOIN"),
    JsonSubTypes.Type(value = LeaveWebSocketMessage::class, name = "LEAVE"),
    JsonSubTypes.Type(value = TypingWebSocketMessage::class, name = "TYPING"),
    JsonSubTypes.Type(value = HeartbeatWebSocketMessage::class, name = "HEARTBEAT"),
    JsonSubTypes.Type(value = SystemWebSocketMessage::class, name = "SYSTEM"),
    JsonSubTypes.Type(value = ErrorWebSocketMessage::class, name = "ERROR"),
    JsonSubTypes.Type(value = AckWebSocketMessage::class, name = "ACK")
)
sealed class WebSocketMessage {
    abstract val type: WebSocketMessageType
    abstract val messageId: String?
    abstract val timestamp: Long // Unix timestamp in milliseconds
    abstract val sessionId: String?
}
