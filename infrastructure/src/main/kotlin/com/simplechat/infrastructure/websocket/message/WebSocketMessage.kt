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