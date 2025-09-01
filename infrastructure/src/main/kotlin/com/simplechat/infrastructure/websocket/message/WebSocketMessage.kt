package com.simplechat.infrastructure.websocket.message

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.Instant

/**
 * WebSocket 메시지 타입 열거형
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
 * WebSocket 메시지 기본 인터페이스
 * 
 * 모든 WebSocket 메시지의 기본 구조를 정의합니다.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ChatMessage::class, name = "CHAT"),
    JsonSubTypes.Type(value = JoinMessage::class, name = "JOIN"),
    JsonSubTypes.Type(value = LeaveMessage::class, name = "LEAVE"),
    JsonSubTypes.Type(value = TypingMessage::class, name = "TYPING"),
    JsonSubTypes.Type(value = HeartbeatMessage::class, name = "HEARTBEAT"),
    JsonSubTypes.Type(value = SystemMessage::class, name = "SYSTEM"),
    JsonSubTypes.Type(value = ErrorMessage::class, name = "ERROR"),
    JsonSubTypes.Type(value = AckMessage::class, name = "ACK")
)
sealed class WebSocketMessage {
    abstract val type: WebSocketMessageType
    abstract val messageId: String?
    abstract val timestamp: Instant
    abstract val sessionId: String?
}

/**
 * 요청 메시지 기본 클래스 (클라이언트 → 서버)
 */
abstract class IncomingWebSocketMessage : WebSocketMessage() {
    abstract val userId: Long?
}

/**
 * 응답 메시지 기본 클래스 (서버 → 클라이언트)  
 */
abstract class OutgoingWebSocketMessage : WebSocketMessage() {
    abstract val targetType: MessageTargetType
    abstract val targets: Set<Long>?  // 사용자 ID 목록 (targetType이 USER인 경우)
    abstract val roomId: Long?        // 채팅방 ID (targetType이 ROOM인 경우)
}

/**
 * 메시지 전송 대상 타입
 */
enum class MessageTargetType {
    USER,       // 특정 사용자들에게
    ROOM,       // 특정 채팅방에
    GLOBAL      // 전체 사용자에게
}

// WebSocket Message 구현체들
data class ChatMessage(
    override val type: WebSocketMessageType = WebSocketMessageType.CHAT,
    override val messageId: String?,
    override val timestamp: Instant,
    override val sessionId: String?,
    val content: String,
    val userId: Long,
    val roomId: Long
) : WebSocketMessage()

data class JoinMessage(
    override val type: WebSocketMessageType = WebSocketMessageType.JOIN,
    override val messageId: String?,
    override val timestamp: Instant,
    override val sessionId: String?,
    val userId: Long,
    val roomId: Long
) : WebSocketMessage()

data class LeaveMessage(
    override val type: WebSocketMessageType = WebSocketMessageType.LEAVE,
    override val messageId: String?,
    override val timestamp: Instant,
    override val sessionId: String?,
    val userId: Long,
    val roomId: Long
) : WebSocketMessage()

data class TypingMessage(
    override val type: WebSocketMessageType = WebSocketMessageType.TYPING,
    override val messageId: String?,
    override val timestamp: Instant,
    override val sessionId: String?,
    val userId: Long,
    val roomId: Long,
    val isTyping: Boolean
) : WebSocketMessage()

data class HeartbeatMessage(
    override val type: WebSocketMessageType = WebSocketMessageType.HEARTBEAT,
    override val messageId: String?,
    override val timestamp: Instant,
    override val sessionId: String?
) : WebSocketMessage()

data class SystemMessage(
    override val type: WebSocketMessageType = WebSocketMessageType.SYSTEM,
    override val messageId: String?,
    override val timestamp: Instant,
    override val sessionId: String?,
    val content: String,
    val level: String = "INFO"
) : WebSocketMessage()

data class ErrorMessage(
    override val type: WebSocketMessageType = WebSocketMessageType.ERROR,
    override val messageId: String?,
    override val timestamp: Instant,
    override val sessionId: String?,
    val errorCode: String,
    val errorMessage: String
) : WebSocketMessage()

data class AckMessage(
    override val type: WebSocketMessageType = WebSocketMessageType.ACK,
    override val messageId: String?,
    override val timestamp: Instant,
    override val sessionId: String?,
    val originalMessageId: String,
    val status: String = "OK"
) : WebSocketMessage()