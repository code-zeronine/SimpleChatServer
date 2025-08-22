package com.simplechat.infrastructure.repository

import com.simplechat.domain.entity.ChatRoom
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import reactor.test.StepVerifier
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("test")
class ChatRoomRepositoryTest {

    @Autowired
    private lateinit var chatRoomRepository: ChatRoomRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    private val testChatRooms = listOf(
        ChatRoom(
            name = "Test Public Room",
            description = "Public test room for general discussion",
            createdBy = 1L,
            isPrivate = false,
            maxParticipants = 100,
            createdAt = LocalDateTime.now().minusHours(5),
            updatedAt = LocalDateTime.now().minusHours(4)
        ),
        ChatRoom(
            name = "Test Private Room",
            description = "Private test room for team members",
            createdBy = 2L,
            isPrivate = true,
            maxParticipants = 10,
            createdAt = LocalDateTime.now().minusHours(3),
            updatedAt = LocalDateTime.now().minusHours(2)
        ),
        ChatRoom(
            name = "Tech Discussion",
            description = "Technical discussions and Q&A",
            createdBy = 1L,
            isPrivate = false,
            maxParticipants = 50,
            createdAt = LocalDateTime.now().minusHours(1),
            updatedAt = LocalDateTime.now().minusMinutes(30)
        )
    )

    @BeforeEach
    fun setUp() {
        // 테스트 전 채팅방 데이터 정리
        StepVerifier.create(chatRoomRepository.deleteAll())
            .verifyComplete()

        // 테스트 채팅방 데이터 삽입
        StepVerifier.create(chatRoomRepository.saveAll(testChatRooms))
            .expectNextCount(3)
            .verifyComplete()
    }

    @Test
    fun `should save chat room successfully`() {
        // Given
        val newChatRoom = ChatRoom(
            name = "New Test Room",
            description = "A newly created test room",
            createdBy = 3L,
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
    fun `should find chat room by id`() {
        // Given - 첫 번째 테스트 룸 조회
        StepVerifier.create(chatRoomRepository.findByName("Test Public Room"))
            .assertNext { room ->
                val roomId = room.id!!

                // When & Then
                StepVerifier.create(chatRoomRepository.findById(roomId))
                    .assertNext { foundRoom ->
                        assertEquals("Test Public Room", foundRoom.name)
                        assertEquals(1L, foundRoom.createdBy)
                        assertEquals(false, foundRoom.isPrivate)
                    }
                    .verifyComplete()
            }
            .verifyComplete()
    }

    @Test
    fun `should find chat room by name`() {
        // Given
        val expectedName = "Tech Discussion"

        // When & Then
        StepVerifier.create(chatRoomRepository.findByName(expectedName))
            .assertNext { room ->
                assertEquals(expectedName, room.name)
                assertEquals("Technical discussions and Q&A", room.description)
                assertEquals(1L, room.createdBy)
            }
            .verifyComplete()
    }

    @Test
    fun `should return empty mono when room name not found`() {
        // Given
        val nonExistentName = "Non-existent Room"

        // When & Then
        StepVerifier.create(chatRoomRepository.findByName(nonExistentName))
            .verifyComplete()
    }

    @Test
    fun `should find chat rooms by creator`() {
        // Given
        val creatorId = 1L

        // When & Then
        StepVerifier.create(chatRoomRepository.findByCreatedBy(creatorId))
            .expectNextCount(2) // 사용자 1이 생성한 방 2개
            .verifyComplete()
    }

    @Test
    fun `should find public rooms only`() {
        // When & Then
        StepVerifier.create(chatRoomRepository.findPublicRooms())
            .expectNextCount(2) // 공개방 2개 (Test Public Room, Tech Discussion)
            .verifyComplete()
    }

    @Test
    fun `should find private rooms only`() {
        // When & Then
        StepVerifier.create(chatRoomRepository.findPrivateRooms())
            .expectNextCount(1) // 비공개방 1개 (Test Private Room)
            .verifyComplete()
    }

    @Test
    fun `should find rooms by name containing pattern`() {
        // Given
        val pattern = "Test"

        // When & Then
        StepVerifier.create(chatRoomRepository.findByNameContaining(pattern))
            .expectNextCount(2) // "Test Public Room", "Test Private Room"
            .verifyComplete()
    }

    @Test
    fun `should find recent rooms with limit`() {
        // Given
        val limit = 2

        // When & Then
        StepVerifier.create(chatRoomRepository.findRecentRooms(limit))
            .assertNext { room ->
                assertEquals("Tech Discussion", room.name) // 가장 최근 생성
            }
            .assertNext { room ->
                assertEquals("Test Private Room", room.name) // 두 번째 최근 생성
            }
            .verifyComplete()
    }

    @Test
    fun `should find rooms created after specific date`() {
        // Given
        val afterDate = LocalDateTime.now().minusHours(2)

        // When & Then
        StepVerifier.create(chatRoomRepository.findRoomsCreatedAfter(afterDate))
            .expectNextCount(1) // Tech Discussion만 2시간 이내에 생성됨
            .verifyComplete()
    }

    @Test
    fun `should find active rooms`() {
        // When & Then - 최근 24시간 내 업데이트된 방들
        StepVerifier.create(chatRoomRepository.findActiveRooms())
            .expectNextCount(3) // 모든 테스트 방이 24시간 이내에 업데이트됨
            .verifyComplete()
    }

    @Test
    fun `should find available public rooms excluding creator`() {
        // Given
        val excludeCreatedBy = 1L

        // When & Then
        StepVerifier.create(chatRoomRepository.findAvailablePublicRooms(excludeCreatedBy))
            .expectNextCount(0) // 사용자 1이 만들지 않은 공개방은 없음 (사용자 2가 만든 방은 비공개)
            .verifyComplete()
    }

    @Test
    fun `should check if room exists by id`() {
        // Given - 첫 번째 룸의 ID 가져오기
        StepVerifier.create(chatRoomRepository.findByName("Test Public Room"))
            .assertNext { room ->
                val roomId = room.id!!

                // When & Then - 존재하는 ID
                StepVerifier.create(chatRoomRepository.existsById(roomId))
                    .expectNext(true)
                    .verifyComplete()

                // When & Then - 존재하지 않는 ID
                StepVerifier.create(chatRoomRepository.existsById(99999L))
                    .expectNext(false)
                    .verifyComplete()
            }
            .verifyComplete()
    }

    @Test
    fun `should check if room exists by name`() {
        // Given
        val existingName = "Test Public Room"
        val nonExistentName = "Non-existent Room"

        // When & Then - 존재하는 이름
        StepVerifier.create(chatRoomRepository.existsByName(existingName))
            .expectNext(true)
            .verifyComplete()

        // When & Then - 존재하지 않는 이름
        StepVerifier.create(chatRoomRepository.existsByName(nonExistentName))
            .expectNext(false)
            .verifyComplete()
    }

    @Test
    fun `should count total rooms`() {
        // When & Then
        StepVerifier.create(chatRoomRepository.countRooms())
            .expectNext(3L) // 테스트 데이터 3개
            .verifyComplete()
    }

    @Test
    fun `should count rooms by creator`() {
        // Given
        val creatorId = 1L

        // When & Then
        StepVerifier.create(chatRoomRepository.countByCreatedBy(creatorId))
            .expectNext(2L) // 사용자 1이 생성한 방 2개
            .verifyComplete()
    }

    @Test
    fun `should update room information`() {
        // Given
        val originalName = "Test Public Room"

        // When - 룸 조회 후 수정
        StepVerifier.create(
            chatRoomRepository.findByName(originalName)
                .flatMap { room ->
                    val updatedRoom = room.copy(
                        name = "Updated Public Room",
                        description = "Updated description",
                        maxParticipants = 150,
                        updatedAt = LocalDateTime.now()
                    )
                    chatRoomRepository.save(updatedRoom)
                }
        )
            .assertNext { updatedRoom ->
                assertEquals("Updated Public Room", updatedRoom.name)
                assertEquals("Updated description", updatedRoom.description)
                assertEquals(150, updatedRoom.maxParticipants)
                assertEquals(1L, updatedRoom.createdBy) // 원래 생성자는 변경되지 않음
            }
            .verifyComplete()

        // Then - 업데이트된 정보 확인
        StepVerifier.create(chatRoomRepository.findByName("Updated Public Room"))
            .assertNext { room ->
                assertEquals("Updated Public Room", room.name)
                assertEquals("Updated description", room.description)
            }
            .verifyComplete()
    }

    @Test
    fun `should delete room successfully`() {
        // Given
        val roomNameToDelete = "Test Private Room"

        // When
        StepVerifier.create(
            chatRoomRepository.findByName(roomNameToDelete)
                .flatMap { room ->
                    chatRoomRepository.delete(room)
                }
        )
            .verifyComplete()

        // Then - 삭제된 룸이 조회되지 않음을 확인
        StepVerifier.create(chatRoomRepository.findByName(roomNameToDelete))
            .verifyComplete()

        // 전체 룸 수 확인
        StepVerifier.create(chatRoomRepository.countRooms())
            .expectNext(2L) // 1개 삭제되어 2개 남음
            .verifyComplete()
    }

    @Test
    fun `should delete room by id`() {
        // Given - 첫 번째 룸의 ID 가져오기
        StepVerifier.create(chatRoomRepository.findByName("Tech Discussion"))
            .assertNext { room ->
                val roomId = room.id!!

                // When
                StepVerifier.create(chatRoomRepository.deleteById(roomId))
                    .verifyComplete()

                // Then - 삭제된 룸이 조회되지 않음을 확인
                StepVerifier.create(chatRoomRepository.findById(roomId))
                    .verifyComplete()
            }
            .verifyComplete()
    }

    @Test
    fun `should delete all rooms by creator`() {
        // Given
        val creatorId = 1L

        // When
        StepVerifier.create(chatRoomRepository.deleteByCreatedBy(creatorId))
            .verifyComplete()

        // Then - 해당 사용자가 생성한 룸들이 모두 삭제됨
        StepVerifier.create(chatRoomRepository.findByCreatedBy(creatorId))
            .verifyComplete()

        // 전체 룸 수 확인 (사용자 1이 생성한 2개 삭제되어 1개만 남음)
        StepVerifier.create(chatRoomRepository.countRooms())
            .expectNext(1L)
            .verifyComplete()
    }

    @Test
    fun `should save room with minimal required fields`() {
        // Given
        val minimalRoom = ChatRoom(
            name = "Minimal Room",
            createdBy = 5L // 사용자가 존재하지 않아도 FK 제약조건 무시하고 테스트
        )

        // When & Then
        StepVerifier.create(chatRoomRepository.save(minimalRoom))
            .assertNext { savedRoom ->
                assertNotNull(savedRoom.id)
                assertEquals("Minimal Room", savedRoom.name)
                assertEquals(null, savedRoom.description)
                assertEquals(5L, savedRoom.createdBy)
                assertEquals(false, savedRoom.isPrivate) // 기본값
                assertEquals(100, savedRoom.maxParticipants) // 기본값
            }
            .verifyComplete()
    }

    @Test
    fun `should handle room with null description`() {
        // Given
        val roomWithNullDescription = ChatRoom(
            name = "No Description Room",
            description = null,
            createdBy = 1L,
            isPrivate = true,
            maxParticipants = 20
        )

        // When & Then
        StepVerifier.create(chatRoomRepository.save(roomWithNullDescription))
            .assertNext { savedRoom ->
                assertEquals("No Description Room", savedRoom.name)
                assertEquals(null, savedRoom.description)
                assertEquals(true, savedRoom.isPrivate)
            }
            .verifyComplete()
    }

    @Test
    fun `should find all rooms`() {
        // When & Then
        StepVerifier.create(chatRoomRepository.findAll())
            .expectNextCount(3) // 모든 테스트 데이터
            .verifyComplete()
    }

    @Test
    fun `should handle case insensitive search`() {
        // Given
        val pattern = "tech" // 소문자로 검색

        // When & Then
        StepVerifier.create(chatRoomRepository.findByNameContaining(pattern))
            .assertNext { room ->
                assertEquals("Tech Discussion", room.name)
            }
            .verifyComplete()
    }

    @Test
    fun `should verify room business logic integration`() {
        // Given
        val newRoom = ChatRoom(
            name = "Business Logic Test Room",
            description = "Testing business logic integration",
            createdBy = 1L,
            maxParticipants = 50
        )

        // When & Then
        StepVerifier.create(chatRoomRepository.save(newRoom))
            .assertNext { savedRoom ->
                // 비즈니스 로직 검증
                assertTrue(savedRoom.isValid())
                assertTrue(savedRoom.isOwnedBy(1L))
                assertEquals(false, savedRoom.isPrivateRoom())
                assertEquals(false, savedRoom.isAtCapacity(25))
                assertEquals(true, savedRoom.isAtCapacity(50))
                assertEquals("Business Logic Test Room", savedRoom.getDisplayName())
            }
            .verifyComplete()
    }
}