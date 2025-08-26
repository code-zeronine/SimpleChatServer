package com.simplechat.infrastructure.repository

import com.simplechat.domain.entity.ChatRoom
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import reactor.test.StepVerifier
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChatRoomRepositoryTest {

    @Autowired
    private lateinit var chatRoomRepository: ChatRoomRepository

    @Test
    fun `should save chat room successfully`() {
        // Given
        val newChatRoom = ChatRoom(
            name = "New Test Room",
            description = "A newly created test room",
            createdBy = 1L, // 기존 사용자 ID 사용
            isPrivate = false,
            maxParticipants = 25
        )

        // When & Then
        StepVerifier.create(chatRoomRepository.save(newChatRoom))
            .assertNext { savedRoom ->
                assertNotNull(savedRoom.id)
                assertEquals(newChatRoom.name, savedRoom.name)
                assertEquals(newChatRoom.description, savedRoom.description)
                assertEquals(newChatRoom.createdBy, savedRoom.createdBy)
                assertEquals(newChatRoom.isPrivate, savedRoom.isPrivate)
                assertEquals(newChatRoom.maxParticipants, savedRoom.maxParticipants)
                assertNotNull(savedRoom.createdAt)
                assertNotNull(savedRoom.updatedAt)
            }
            .verifyComplete()
    }

    @Test
    fun `should find chat room by name`() {
        // Given & When & Then - data.sql의 기존 데이터 사용
        StepVerifier.create(chatRoomRepository.findByName("General Chat"))
            .assertNext { room ->
                assertEquals("General Chat", room.name)
                assertEquals("일반 대화를 위한 공개 채팅방", room.description)
                assertEquals(1L, room.createdBy)
                assertEquals(false, room.isPrivate)
                assertEquals(100, room.maxParticipants)
                assertNotNull(room.id)
                assertNotNull(room.createdAt)
                assertNotNull(room.updatedAt)
            }
            .verifyComplete()
    }

    @Test
    fun `should find all chat rooms`() {
        // When & Then - data.sql에 5개의 채팅방이 있음
        StepVerifier.create(chatRoomRepository.findAll())
            .expectNextMatches { it.name == "General Chat" }
            .expectNextMatches { it.name == "Tech Talk" }
            .expectNextMatches { it.name == "Private Discussion" }
            .expectNextMatches { it.name == "Team Project" }
            .expectNextMatches { it.name == "Random Chat" }
            .expectNextMatches { it.name == "New Test Room" }
            .verifyComplete()
    }

    @Test
    fun `should find public chat rooms`() {
        // When & Then
        StepVerifier.create(chatRoomRepository.findPublicRooms())
            .expectNextCount(4) // General Chat, Tech Talk, Team Project, Random Chat
            .verifyComplete()
    }

    @Test
    fun `should find private chat rooms`() {
        // When & Then
        StepVerifier.create(chatRoomRepository.findPrivateRooms())
            .expectNextCount(1) // Private Discussion
            .verifyComplete()
    }
}