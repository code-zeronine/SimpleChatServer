package com.simplechat.domain.message

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Instant

/**
 * WebSocket 메시지 타입 열거형 (Domain)
 */
enum class WebSocketMessageType {
    CHAT,           // 채팅 메시지
    JOIN,           // 채팅방 입장
    LEAVE,          // 채팅방 퇴장
    TYPING,         // 타이핑 상태
    HEARTBEAT,      // 하트비트
    SYSTEM,         // 시스템 메시지
    ERROR,          // 에러 메시지
    ACK             // 확인 메시지
}

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
    abstract val timestamp: Instant
    abstract val sessionId: String?
}

/**
 * 메시지 전송 대상 타입
 */
enum class MessageTargetType {
    USER,       // 특정 사용자들에게
    ROOM,       // 특정 채팅방에
    SESSION,    // 특정 세션에게
    GLOBAL      // 전체 사용자에게
}

// WebSocket Message 구현체들 (Domain)
data class ChatWebSocketMessage(
    override val type: WebSocketMessageType = WebSocketMessageType.CHAT,
    override val messageId: String?,
    override val timestamp: Instant,
    override val sessionId: String?,
    val content: String,
    val userId: Long,
    val roomId: Long,
    val userNickname: String
) : WebSocketMessage()

data class JoinWebSocketMessage(
    override val type: WebSocketMessageType = WebSocketMessageType.JOIN,
    override val messageId: String?,
    override val timestamp: Instant,
    override val sessionId: String?,
    val userId: Long,
    val roomId: Long,
    val userNickname: String
) : WebSocketMessage()

data class LeaveWebSocketMessage(
    override val type: WebSocketMessageType = WebSocketMessageType.LEAVE,
    override val messageId: String?,
    override val timestamp: Instant,
    override val sessionId: String?,
    val userId: Long,
    val roomId: Long,
    val userNickname: String
) : WebSocketMessage()

data class TypingWebSocketMessage(
    override val type: WebSocketMessageType = WebSocketMessageType.TYPING,
    override val messageId: String?,
    override val timestamp: Instant,
    override val sessionId: String?,
    val userId: Long,
    val roomId: Long,
    val userNickname: String,
    val isTyping: Boolean
) : WebSocketMessage()

data class HeartbeatWebSocketMessage(
    override val type: WebSocketMessageType = WebSocketMessageType.HEARTBEAT,
    override val messageId: String?,
    override val timestamp: Instant,
    override val sessionId: String?
) : WebSocketMessage()

data class SystemWebSocketMessage(
    override val type: WebSocketMessageType = WebSocketMessageType.SYSTEM,
    override val messageId: String?,
    override val timestamp: Instant,
    override val sessionId: String?,
    val content: String,
    val level: String = "INFO"
) : WebSocketMessage()

data class ErrorWebSocketMessage(
    override val type: WebSocketMessageType = WebSocketMessageType.ERROR,
    override val messageId: String?,
    override val timestamp: Instant,
    override val sessionId: String?,
    val errorCode: String,
    val errorMessage: String,
    val originalMessage: String? = null,
    val details: Map<String, Any>? = null
) : WebSocketMessage()

data class AckWebSocketMessage(
    override val type: WebSocketMessageType = WebSocketMessageType.ACK,
    override val messageId: String?,
    override val timestamp: Instant,
    override val sessionId: String?,
    val originalMessageId: String,
    val status: String = "OK"
) : WebSocketMessage()