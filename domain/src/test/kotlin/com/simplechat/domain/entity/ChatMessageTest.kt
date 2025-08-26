package com.simplechat.domain.entity

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.*

class ChatMessageTest {

    @Test
    fun `ChatMessage 생성 테스트`() {
        // Given
        val roomId = 1L
        val userId = 100L
        val content = "안녕하세요!"
        val timestamp = LocalDateTime.now()
        val messageType = MessageType.TEXT

        // When
        val chatMessage = ChatMessage(
            roomId = roomId,
            userId = userId,
            content = content,
            timestamp = timestamp,
            messageType = messageType
        )

        // Then
        assertNull(chatMessage.id)
        assertEquals(roomId, chatMessage.roomId)
        assertEquals(userId, chatMessage.userId)
        assertEquals(content, chatMessage.content)
        assertEquals(timestamp, chatMessage.timestamp)
        assertEquals(messageType, chatMessage.messageType)
    }

    @Test
    fun `ChatMessage 기본값 테스트`() {
        // When
        val chatMessage = ChatMessage(
            roomId = 1L,
            userId = 100L,
            content = "테스트"
        )

        // Then
        assertNull(chatMessage.id)
        assertEquals(MessageType.TEXT, chatMessage.messageType)
        assertNotNull(chatMessage.timestamp)
        assertTrue(chatMessage.timestamp <= LocalDateTime.now())
    }

    @Test
    fun `isValid 메서드 테스트 - 유효한 메시지`() {
        // Given
        val validMessage = ChatMessage(
            roomId = 1L,
            userId = 100L,
            content = "유효한 메시지입니다."
        )

        // When & Then
        assertTrue(validMessage.isValid())
    }

    @Test
    fun `isValid 메서드 테스트 - 무효한 메시지들`() {
        // Given - roomId가 0 이하
        val invalidRoomId = ChatMessage(
            roomId = 0L,
            userId = 100L,
            content = "테스트"
        )

        // Given - userId가 0 이하
        val invalidUserId = ChatMessage(
            roomId = 1L,
            userId = -1L,
            content = "테스트"
        )

        // Given - content가 빈 문자열
        val emptyContent = ChatMessage(
            roomId = 1L,
            userId = 100L,
            content = ""
        )

        // Given - content가 1000자 초과
        val longContent = ChatMessage(
            roomId = 1L,
            userId = 100L,
            content = "a".repeat(1001)
        )

        // When & Then
        assertFalse(invalidRoomId.isValid())
        assertFalse(invalidUserId.isValid())
        assertFalse(emptyContent.isValid())
        assertFalse(longContent.isValid())
    }

    @Test
    fun `isSystemMessage 메서드 테스트`() {
        // Given
        val systemMessage = ChatMessage(roomId = 1L, userId = 100L, content = "시스템 메시지", messageType = MessageType.SYSTEM)
        val joinMessage = ChatMessage(roomId = 1L, userId = 100L, content = "참여 메시지", messageType = MessageType.JOIN)
        val leaveMessage = ChatMessage(roomId = 1L, userId = 100L, content = "퇴장 메시지", messageType = MessageType.LEAVE)
        val textMessage = ChatMessage(roomId = 1L, userId = 100L, content = "일반 메시지", messageType = MessageType.TEXT)

        // When & Then
        assertTrue(systemMessage.isSystemMessage())
        assertTrue(joinMessage.isSystemMessage())
        assertTrue(leaveMessage.isSystemMessage())
        assertFalse(textMessage.isSystemMessage())
    }

    @Test
    fun `isUserMessage 메서드 테스트`() {
        // Given
        val textMessage = ChatMessage(roomId = 1L, userId = 100L, content = "사용자 메시지", messageType = MessageType.TEXT)
        val systemMessage = ChatMessage(roomId = 1L, userId = 100L, content = "시스템 메시지", messageType = MessageType.SYSTEM)

        // When & Then
        assertTrue(textMessage.isUserMessage())
        assertFalse(systemMessage.isUserMessage())
    }

    @Test
    fun `getMessageInfo 메서드 테스트`() {
        // Given
        val chatMessage = ChatMessage(
            roomId = 1L,
            userId = 100L,
            content = "테스트 메시지",
            messageType = MessageType.TEXT
        )

        // When
        val messageInfo = chatMessage.getMessageInfo()

        // Then
        assertTrue(messageInfo.contains("roomId=1"))
        assertTrue(messageInfo.contains("userId=100"))
        assertTrue(messageInfo.contains("type=TEXT"))
        assertTrue(messageInfo.contains("timestamp="))
    }

    @Test
    fun `equals와 hashCode 테스트`() {
        // Given
        val message1 = ChatMessage(id = "1", roomId = 1L, userId = 100L, content = "테스트")
        val message2 = ChatMessage(id = "1", roomId = 2L, userId = 200L, content = "다른 내용")
        val message3 = ChatMessage(id = "2", roomId = 1L, userId = 100L, content = "테스트")
        val message4 = ChatMessage(roomId = 1L, userId = 100L, content = "테스트") // id가 null

        // When & Then
        assertEquals(message1, message2) // 같은 id
        assertNotEquals(message1, message3) // 다른 id
        assertNotEquals(message1, message4) // 하나는 id가 null
        
        assertEquals(message1.hashCode(), message2.hashCode()) // 같은 id의 hashCode
        assertEquals(0, message4.hashCode()) // id가 null인 경우 hashCode는 0
    }

    @Test
    fun `toString 테스트`() {
        // Given
        val chatMessage = ChatMessage(
            id = "test-id",
            roomId = 1L,
            userId = 100L,
            content = "테스트 메시지",
            messageType = MessageType.TEXT
        )

        // When
        val result = chatMessage.toString()

        // Then
        assertTrue(result.contains("id=test-id"))
        assertTrue(result.contains("roomId=1"))
        assertTrue(result.contains("userId=100"))
        assertTrue(result.contains("messageType=TEXT"))
        assertTrue(result.contains("ChatMessage"))
    }
}