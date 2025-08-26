package com.simplechat.service

import com.simplechat.domain.entity.ChatMessage
import com.simplechat.domain.entity.MessageType
import com.simplechat.infrastructure.entity.ChatMessageEntity
import com.simplechat.infrastructure.repository.ChatMessageRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageRequest
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.LocalDateTime

class ChatMessageServiceTest {

    private val repository: ChatMessageRepository = mockk()
    private lateinit var service: ChatMessageService

    @BeforeEach
    fun setUp() {
        service = ChatMessageService(repository)
    }

    @Test
    fun `should save valid chat message`() {
        // Given
        val chatMessage = ChatMessage(
            roomId = 1L,
            userId = 100L,
            content = "테스트 메시지",
            messageType = MessageType.TEXT
        )
        
        val savedEntity = ChatMessageEntity(
            id = "test-id",
            roomId = 1L,
            userId = 100L,
            content = "테스트 메시지",
            messageType = MessageType.TEXT,
            timestamp = LocalDateTime.now()
        )
        
        every { repository.save(any<ChatMessageEntity>()) } returns Mono.just(savedEntity)

        // When & Then
        StepVerifier.create(service.save(chatMessage))
            .expectNextMatches { savedMessage ->
                savedMessage.id == "test-id" &&
                savedMessage.roomId == 1L &&
                savedMessage.content == "테스트 메시지" &&
                savedMessage.messageType == MessageType.TEXT
            }
            .verifyComplete()
            
        verify { repository.save(any<ChatMessageEntity>()) }
    }

    @Test
    fun `should reject invalid chat message`() {
        // Given
        val invalidMessage = ChatMessage(
            roomId = 0L, // invalid
            userId = 100L,
            content = "",
            messageType = MessageType.TEXT
        )

        // When & Then
        assertThrows<IllegalArgumentException> {
            service.save(invalidMessage).block()
        }
    }

    @Test
    fun `should find messages by room id`() {
        // Given
        val roomId = 1L
        val pageable = PageRequest.of(0, 10)
        
        val entity1 = ChatMessageEntity(
            id = "test-id-1",
            roomId = roomId,
            userId = 100L,
            content = "메시지 1",
            messageType = MessageType.TEXT,
            timestamp = LocalDateTime.now()
        )
        val entity2 = ChatMessageEntity(
            id = "test-id-2",
            roomId = roomId,
            userId = 101L,
            content = "메시지 2",
            messageType = MessageType.TEXT,
            timestamp = LocalDateTime.now()
        )
        
        every { repository.findByRoomIdOrderByTimestampDesc(roomId, pageable) } returns 
            Flux.just(entity1, entity2)

        // When & Then
        StepVerifier.create(service.findByRoomId(roomId, pageable))
            .expectNextCount(2)
            .verifyComplete()
            
        verify { repository.findByRoomIdOrderByTimestampDesc(roomId, pageable) }
    }

    @Test
    fun `should count messages by room id`() {
        // Given
        val roomId = 1L
        
        every { repository.countByRoomId(roomId) } returns Mono.just(3L)

        // When & Then
        StepVerifier.create(service.countByRoomId(roomId))
            .expectNext(3L)
            .verifyComplete()
            
        verify { repository.countByRoomId(roomId) }
    }

    @Test
    fun `should create system message`() {
        // Given
        val roomId = 1L
        val content = "시스템 메시지"
        val messageType = MessageType.SYSTEM
        
        val savedEntity = ChatMessageEntity(
            id = "system-id",
            roomId = roomId,
            userId = 0L,
            content = content,
            messageType = messageType,
            timestamp = LocalDateTime.now()
        )
        
        every { repository.save(any<ChatMessageEntity>()) } returns Mono.just(savedEntity)

        // When & Then
        StepVerifier.create(service.createSystemMessage(roomId, content, messageType))
            .expectNextMatches { message ->
                message.id == "system-id" &&
                message.roomId == roomId &&
                message.content == content &&
                message.messageType == messageType &&
                message.userId == 0L
            }
            .verifyComplete()
            
        verify { repository.save(any<ChatMessageEntity>()) }
    }

    @Test
    fun `should create join message`() {
        // Given
        val roomId = 1L
        val userId = 100L
        val userNickname = "테스터"
        val expectedContent = "테스터 님이 채팅방에 참여했습니다."
        
        val savedEntity = ChatMessageEntity(
            id = "join-id",
            roomId = roomId,
            userId = 0L,
            content = expectedContent,
            messageType = MessageType.JOIN,
            timestamp = LocalDateTime.now()
        )
        
        every { repository.save(any<ChatMessageEntity>()) } returns Mono.just(savedEntity)

        // When & Then
        StepVerifier.create(service.createJoinMessage(roomId, userId, userNickname))
            .expectNextMatches { message ->
                message.id == "join-id" &&
                message.roomId == roomId &&
                message.content == expectedContent &&
                message.messageType == MessageType.JOIN &&
                message.userId == 0L
            }
            .verifyComplete()
            
        verify { repository.save(any<ChatMessageEntity>()) }
    }

    @Test
    fun `should reject invalid room id`() {
        // Given
        val invalidRoomId = 0L
        val pageable = PageRequest.of(0, 10)

        // When & Then
        assertThrows<IllegalArgumentException> {
            service.findByRoomId(invalidRoomId, pageable).blockFirst()
        }
    }

    @Test
    fun `should reject system message with TEXT type`() {
        // Given
        val roomId = 1L
        val content = "시스템 메시지"
        val messageType = MessageType.TEXT // invalid for system message

        // When & Then
        assertThrows<IllegalArgumentException> {
            service.createSystemMessage(roomId, content, messageType).block()
        }
    }
}