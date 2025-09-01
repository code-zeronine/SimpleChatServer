package com.simplechat.dto

// WebSocket message types have been moved to domain.message package
// This file is kept for API layer specific DTOs only

import com.simplechat.domain.message.AckWebSocketMessage
import com.simplechat.domain.message.ChatWebSocketMessage
import com.simplechat.domain.message.ErrorWebSocketMessage
import com.simplechat.domain.message.HeartbeatWebSocketMessage
import com.simplechat.domain.message.JoinWebSocketMessage
import com.simplechat.domain.message.LeaveWebSocketMessage
import com.simplechat.domain.message.SystemWebSocketMessage
import com.simplechat.domain.message.TypingWebSocketMessage
import com.simplechat.domain.message.WebSocketMessage
import java.time.Instant

/**
 * WebSocket 메시지를 위한 API 계층 어댑터
 */
object WebSocketMessageAdapter {
    
    /**
     * API 계층의 요청을 도메인 메시지로 변환
     */
    fun fromApiRequest(
        type: String,
        data: Map<String, Any>,
        sessionId: String?,
        messageId: String? = null,
        timestamp: Instant = Instant.now()
    ): WebSocketMessage {
        return when (type) {
            "CHAT" -> ChatWebSocketMessage(
                messageId = messageId,
                timestamp = timestamp,
                sessionId = sessionId,
                content = data["content"] as String,
                userId = (data["userId"] as Number).toLong(),
                roomId = (data["roomId"] as Number).toLong()
            )
            "JOIN" -> JoinWebSocketMessage(
                messageId = messageId,
                timestamp = timestamp,
                sessionId = sessionId,
                userId = (data["userId"] as Number).toLong(),
                roomId = (data["roomId"] as Number).toLong(),
                userNickname = data["userNickname"] as String
            )
            "LEAVE" -> LeaveWebSocketMessage(
                messageId = messageId,
                timestamp = timestamp,
                sessionId = sessionId,
                userId = (data["userId"] as Number).toLong(),
                roomId = (data["roomId"] as Number).toLong(),
                userNickname = data["userNickname"] as String
            )
            "TYPING" -> TypingWebSocketMessage(
                messageId = messageId,
                timestamp = timestamp,
                sessionId = sessionId,
                userId = (data["userId"] as Number).toLong(),
                roomId = (data["roomId"] as Number).toLong(),
                userNickname = data["userNickname"] as String,
                isTyping = data["isTyping"] as Boolean
            )
            else -> throw IllegalArgumentException("Unknown WebSocket message type: $type")
        }
    }
    
    /**
     * 도메인 메시지를 API 응답으로 변환
     */
    fun toApiResponse(message: WebSocketMessage): Map<String, Any> {
        val baseMap = mutableMapOf<String, Any>(
            "type" to message.type.name,
            "messageId" to (message.messageId ?: ""),
            "timestamp" to message.timestamp,
            "sessionId" to (message.sessionId ?: "")
        )
        
        when (message) {
            is ChatWebSocketMessage -> baseMap.putAll(mapOf(
                "content" to message.content,
                "userId" to message.userId,
                "roomId" to message.roomId
            ))
            is JoinWebSocketMessage -> baseMap.putAll(mapOf(
                "userId" to message.userId,
                "roomId" to message.roomId,
                "userNickname" to message.userNickname
            ))
            is LeaveWebSocketMessage -> baseMap.putAll(mapOf(
                "userId" to message.userId,
                "roomId" to message.roomId,
                "userNickname" to message.userNickname
            ))
            is TypingWebSocketMessage -> baseMap.putAll(mapOf(
                "userId" to message.userId,
                "roomId" to message.roomId,
                "userNickname" to message.userNickname,
                "isTyping" to message.isTyping
            ))
            is SystemWebSocketMessage -> baseMap.putAll(mapOf(
                "content" to message.content,
                "level" to message.level
            ))
            is ErrorWebSocketMessage -> baseMap.putAll(mapOf(
                "errorCode" to message.errorCode,
                "errorMessage" to message.errorMessage
            ))
            is HeartbeatWebSocketMessage -> {
                // 기본 정보만 포함
            }
            is AckWebSocketMessage -> baseMap.putAll(mapOf(
                "originalMessageId" to message.originalMessageId,
                "status" to message.status
            ))
        }
        
        return baseMap
    }
}
