package com.simplechat.domain.entity

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDateTime

@Document(collection = "chat_messages")
@CompoundIndex(def = "{'roomId': 1, 'timestamp': -1}", name = "idx_roomId_timestamp")
data class ChatMessage(
    @Id
    val id: String? = null,
    
    @Indexed
    val roomId: Long,
    
    val userId: Long,
    
    val content: String,
    
    @Indexed
    val timestamp: LocalDateTime = LocalDateTime.now(),
    
    val messageType: MessageType = MessageType.TEXT
)

enum class MessageType {
    TEXT,
    SYSTEM,
    JOIN,
    LEAVE
}