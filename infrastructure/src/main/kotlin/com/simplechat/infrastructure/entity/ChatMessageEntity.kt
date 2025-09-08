package com.simplechat.infrastructure.entity

import com.simplechat.domain.entity.ChatMessage
import com.simplechat.domain.entity.MessageType
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.index.TextIndexed
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import java.time.LocalDateTime

/**
 * ChatMessage 도메인 엔티티의 MongoDB 매핑을 위한 데이터베이스 엔티티
 * 
 * MongoDB 컬렉션과 도메인 모델 간의 매핑 역할을 수행합니다.
 */
@Document(collection = "chat_messages")
@CompoundIndex(def = "{'roomId': 1, 'timestamp': -1}", name = "idx_roomId_timestamp")
@CompoundIndex(def = "{'roomId': 1, 'messageType': 1, 'timestamp': -1}", name = "idx_roomId_messageType_timestamp")
@CompoundIndex(def = "{'userId': 1, 'timestamp': -1}", name = "idx_userId_timestamp")
data class ChatMessageEntity(
    @Id
    val id: String? = null,
    
    @Field("roomId")
    @Indexed
    val roomId: Long,
    
    @Field("userId")
    val userId: Long,
    
    @Field("content")
    @TextIndexed
    val content: String,
    
    @Field("timestamp")
    @Indexed
    val timestamp: LocalDateTime = LocalDateTime.now(java.time.ZoneOffset.UTC),
    
    @Field("messageType")
    @Indexed
    val messageType: MessageType = MessageType.TEXT
) {
    /**
     * ChatMessageEntity를 도메인 ChatMessage 객체로 변환합니다.
     */
    fun toDomain(): ChatMessage {
        return ChatMessage(
            id = this.id,
            roomId = this.roomId,
            userId = this.userId,
            content = this.content,
            timestamp = this.timestamp,
            messageType = this.messageType
        )
    }
    
    companion object {
        /**
         * 도메인 ChatMessage 객체를 ChatMessageEntity로 변환합니다.
         */
        fun fromDomain(chatMessage: ChatMessage): ChatMessageEntity {
            return ChatMessageEntity(
                id = chatMessage.id,
                roomId = chatMessage.roomId,
                userId = chatMessage.userId,
                content = chatMessage.content,
                timestamp = chatMessage.timestamp,
                messageType = chatMessage.messageType
            )
        }
    }
}