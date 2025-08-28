package com.simplechat.dto

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ChatMessage::class, name = "CHAT"),
    JsonSubTypes.Type(value = JoinMessage::class, name = "JOIN"),
    JsonSubTypes.Type(value = LeaveMessage::class, name = "LEAVE"),
    JsonSubTypes.Type(value = TypingMessage::class, name = "TYPING")
)
sealed class WebSocketMessage {
    abstract val type: WebSocketActionType
}

enum class WebSocketActionType {
    CHAT, JOIN, LEAVE, TYPING
}

data class ChatMessage(
    val chatRoomId: String,
    val senderId: String,
    val senderNickname: String,
    val content: String
) : WebSocketMessage() {
    override val type: WebSocketActionType = WebSocketActionType.CHAT
}


data class JoinMessage(
    val chatRoomId: String,
    val userId: String,
    val userNickname: String
) : WebSocketMessage() {
    override val type: WebSocketActionType = WebSocketActionType.JOIN
}

data class LeaveMessage(
    val chatRoomId: String,
    val userId: String,
    val userNickname: String
) : WebSocketMessage() {
    override val type: WebSocketActionType = WebSocketActionType.LEAVE
}

data class TypingMessage(
    val chatRoomId: String,
    val userId: String,
    val userNickname: String,
    val isTyping: Boolean
) : WebSocketMessage() {
    override val type: WebSocketActionType = WebSocketActionType.TYPING
}
