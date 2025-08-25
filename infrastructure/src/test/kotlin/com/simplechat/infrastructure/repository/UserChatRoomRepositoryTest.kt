package com.simplechat.infrastructure.repository

import com.simplechat.domain.entity.ChatRoomRole
import com.simplechat.domain.entity.UserChatRoom
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import reactor.test.StepVerifier
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("test")
class UserChatRoomRepositoryTest {

    @Autowired
    private lateinit var userChatRoomRepository: UserChatRoomRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var chatRoomRepository: ChatRoomRepository

    private val testRelationships = listOf(
        UserChatRoom(
            userId = 1L,
            chatRoomId = 1L,
            role = ChatRoomRole.OWNER,
            joinedAt = LocalDateTime.now().minusHours(2),
            isActive = true,
            lastReadAt = LocalDateTime.now().minusMinutes(10),
            isPinned = true
        ),
        UserChatRoom(
            userId = 2L,
            chatRoomId = 1L,
            role = ChatRoomRole.ADMIN,
            joinedAt = LocalDateTime.now().minusHours(1),
            isActive = true,
            lastReadAt = LocalDateTime.now().minusMinutes(5),
            invitedBy = 1L
        ),
        UserChatRoom(
            userId = 3L,
            chatRoomId = 1L,
            role = ChatRoomRole.MEMBER,
            joinedAt = LocalDateTime.now().minusMinutes(45),
            isActive = true,
            isMuted = true,
            invitedBy = 1L
        ),
        UserChatRoom(
            userId = 1L,
            chatRoomId = 2L,
            role = ChatRoomRole.ADMIN,
            joinedAt = LocalDateTime.now().minusHours(3),
            isActive = true,
            lastReadAt = LocalDateTime.now().minusMinutes(30)
        ),
        UserChatRoom(
            userId = 4L,
            chatRoomId = 2L,
            role = ChatRoomRole.MEMBER,
            joinedAt = LocalDateTime.now().minusHours(1),
            isActive = false,
            leftAt = LocalDateTime.now().minusMinutes(20),
            invitedBy = 1L
        )
    )

    @BeforeEach
    fun setUp() {
        // 테스트 전 데이터 정리
        StepVerifier.create(userChatRoomRepository.deleteAll())
            .verifyComplete()

        // 테스트 데이터 삽입
        StepVerifier.create(userChatRoomRepository.saveAll(testRelationships))
            .expectNextCount(5)
            .verifyComplete()
    }

    @Test
    fun `should save user chat room relationship successfully`() {
        // Given
        val newRelationship = UserChatRoom(
            userId = 5L,
            chatRoomId = 1L,
            role = ChatRoomRole.MEMBER,
            isActive = true,
            invitedBy = 2L
        )

        // When & Then
        StepVerifier.create(userChatRoomRepository.save(newRelationship))
            .assertNext { saved ->
                assertEquals(5L, saved.userId)
                assertEquals(1L, saved.chatRoomId)
                assertEquals(ChatRoomRole.MEMBER, saved.role)
                assertTrue(saved.isActive)
                assertEquals(2L, saved.invitedBy)
                assertNotNull(saved.joinedAt)
                assertNotNull(saved.updatedAt)
            }
            .verifyComplete()
    }

    @Test
    fun `should find relationship by user id and chat room id`() {
        // Given
        val userId = 1L
        val chatRoomId = 1L

        // When & Then
        StepVerifier.create(userChatRoomRepository.findByUserIdAndChatRoomId(userId, chatRoomId))
            .assertNext { relationship ->
                assertEquals(userId, relationship.userId)
                assertEquals(chatRoomId, relationship.chatRoomId)
                assertEquals(ChatRoomRole.OWNER, relationship.role)
                assertTrue(relationship.isPinned)
            }
            .verifyComplete()
    }

    @Test
    fun `should return empty mono when relationship not found`() {
        // Given
        val nonExistentUserId = 999L
        val nonExistentChatRoomId = 999L

        // When & Then
        StepVerifier.create(userChatRoomRepository.findByUserIdAndChatRoomId(nonExistentUserId, nonExistentChatRoomId))
            .verifyComplete()
    }

    @Test
    fun `should find relationships by user id`() {
        // Given
        val userId = 1L

        // When & Then
        StepVerifier.create(userChatRoomRepository.findByUserId(userId))
            .expectNextCount(2) // User 1은 2개 채팅방에 참여
            .verifyComplete()
    }

    @Test
    fun `should find active rooms by user id`() {
        // Given
        val userId = 1L

        // When & Then
        StepVerifier.create(userChatRoomRepository.findActiveRoomsByUserId(userId))
            .expectNextCount(2) // User 1은 2개 활성 채팅방에 참여
            .verifyComplete()
    }

    @Test
    fun `should find relationships by chat room id`() {
        // Given
        val chatRoomId = 1L

        // When & Then
        StepVerifier.create(userChatRoomRepository.findByChatRoomId(chatRoomId))
            .expectNextCount(3) // Chat room 1에는 3명 참여
            .verifyComplete()
    }

    @Test
    fun `should find active participants by chat room id`() {
        // Given
        val chatRoomId = 1L

        // When & Then
        StepVerifier.create(userChatRoomRepository.findActiveParticipantsByChatRoomId(chatRoomId))
            .expectNextCount(3) // Chat room 1에는 3명이 활성 참여 중
            .verifyComplete()
    }

    @Test
    fun `should find participants by chat room id and role`() {
        // Given
        val chatRoomId = 1L

        // When & Then - OWNER
        StepVerifier.create(userChatRoomRepository.findByChatRoomIdAndRole(chatRoomId, ChatRoomRole.OWNER))
            .expectNextCount(1)
            .verifyComplete()

        // When & Then - ADMIN
        StepVerifier.create(userChatRoomRepository.findByChatRoomIdAndRole(chatRoomId, ChatRoomRole.ADMIN))
            .expectNextCount(1)
            .verifyComplete()

        // When & Then - MEMBER
        StepVerifier.create(userChatRoomRepository.findByChatRoomIdAndRole(chatRoomId, ChatRoomRole.MEMBER))
            .expectNextCount(1)
            .verifyComplete()
    }

    @Test
    fun `should find owner by chat room id`() {
        // Given
        val chatRoomId = 1L

        // When & Then
        StepVerifier.create(userChatRoomRepository.findOwnerByChatRoomId(chatRoomId))
            .assertNext { owner ->
                assertEquals(1L, owner.userId)
                assertEquals(chatRoomId, owner.chatRoomId)
                assertEquals(ChatRoomRole.OWNER, owner.role)
            }
            .verifyComplete()
    }

    @Test
    fun `should find admins by chat room id`() {
        // Given
        val chatRoomId = 1L

        // When & Then
        StepVerifier.create(userChatRoomRepository.findAdminsByChatRoomId(chatRoomId))
            .expectNextCount(2) // OWNER(1) + ADMIN(1) = 2
            .verifyComplete()
    }

    @Test
    fun `should find pinned rooms by user id`() {
        // Given
        val userId = 1L

        // When & Then
        StepVerifier.create(userChatRoomRepository.findPinnedRoomsByUserId(userId))
            .expectNextCount(1) // User 1은 1개 방을 고정
            .verifyComplete()
    }

    @Test
    fun `should find muted rooms by user id`() {
        // Given
        val userId = 3L

        // When & Then
        StepVerifier.create(userChatRoomRepository.findMutedRoomsByUserId(userId))
            .expectNextCount(1) // User 3은 1개 방을 음소거
            .verifyComplete()
    }

    @Test
    fun `should find unread rooms by user id`() {
        // Given
        val userId = 3L // User 3은 lastReadAt이 null

        // When & Then
        StepVerifier.create(userChatRoomRepository.findUnreadRoomsByUserId(userId))
            .expectNextCount(1) // User 3은 읽지 않은 메시지가 있을 가능성이 있는 방이 1개
            .verifyComplete()
    }

    @Test
    fun `should find relationships by invited by`() {
        // Given
        val inviterId = 1L

        // When & Then
        StepVerifier.create(userChatRoomRepository.findByInvitedBy(inviterId))
            .expectNextCount(3) // User 1이 초대한 관계는 3개
            .verifyComplete()
    }

    @Test
    fun `should find recent rooms by user id with limit`() {
        // Given
        val userId = 1L
        val limit = 1

        // When & Then
        StepVerifier.create(userChatRoomRepository.findRecentRoomsByUserId(userId, limit))
            .expectNextCount(1) // 최근 1개만 반환
            .verifyComplete()
    }

    @Test
    fun `should find relationships joined after specific date`() {
        // Given
        val afterDate = LocalDateTime.now().minusHours(2)

        // When & Then
        StepVerifier.create(userChatRoomRepository.findByJoinedAfter(afterDate))
            .expectNextCount(3) // 2시간 이내에 참여한 관계는 3개
            .verifyComplete()
    }

    @Test
    fun `should find left participants by chat room id`() {
        // Given
        val chatRoomId = 2L

        // When & Then
        StepVerifier.create(userChatRoomRepository.findLeftParticipantsByChatRoomId(chatRoomId))
            .expectNextCount(1) // Chat room 2에서 나간 사용자는 1명
            .verifyComplete()
    }

    @Test
    fun `should check if relationship exists`() {
        // Given
        val existingUserId = 1L
        val existingChatRoomId = 1L
        val nonExistentUserId = 999L
        val nonExistentChatRoomId = 999L

        // When & Then - existing relationship
        StepVerifier.create(userChatRoomRepository.existsByUserIdAndChatRoomId(existingUserId, existingChatRoomId))
            .expectNext(true)
            .verifyComplete()

        // When & Then - non-existent relationship
        StepVerifier.create(userChatRoomRepository.existsByUserIdAndChatRoomId(nonExistentUserId, nonExistentChatRoomId))
            .expectNext(false)
            .verifyComplete()
    }

    @Test
    fun `should check if user is active participant`() {
        // Given
        val activeUserId = 1L
        val chatRoomId = 1L
        val inactiveUserId = 4L
        val inactiveChatRoomId = 2L

        // When & Then - active participant
        StepVerifier.create(userChatRoomRepository.isActiveParticipant(activeUserId, chatRoomId))
            .expectNext(true)
            .verifyComplete()

        // When & Then - inactive participant
        StepVerifier.create(userChatRoomRepository.isActiveParticipant(inactiveUserId, inactiveChatRoomId))
            .expectNext(false)
            .verifyComplete()
    }

    @Test
    fun `should count active participants by chat room id`() {
        // Given
        val chatRoomId = 1L

        // When & Then
        StepVerifier.create(userChatRoomRepository.countActiveParticipantsByChatRoomId(chatRoomId))
            .expectNext(3L) // Chat room 1에는 3명이 활성 참여
            .verifyComplete()
    }

    @Test
    fun `should count active rooms by user id`() {
        // Given
        val userId = 1L

        // When & Then
        StepVerifier.create(userChatRoomRepository.countActiveRoomsByUserId(userId))
            .expectNext(2L) // User 1은 2개 방에 활성 참여
            .verifyComplete()
    }

    @Test
    fun `should count by role`() {
        // When & Then - OWNER
        StepVerifier.create(userChatRoomRepository.countByRole(ChatRoomRole.OWNER))
            .expectNext(1L) // OWNER는 1명
            .verifyComplete()

        // When & Then - ADMIN
        StepVerifier.create(userChatRoomRepository.countByRole(ChatRoomRole.ADMIN))
            .expectNext(1L) // ADMIN은 1명 (활성 상태만)
            .verifyComplete()

        // When & Then - MEMBER
        StepVerifier.create(userChatRoomRepository.countByRole(ChatRoomRole.MEMBER))
            .expectNext(1L) // MEMBER는 1명 (활성 상태만)
            .verifyComplete()
    }

    @Test
    fun `should update relationship successfully`() {
        // Given
        val userId = 2L
        val chatRoomId = 1L

        // When - Find and update role
        StepVerifier.create(
            userChatRoomRepository.findByUserIdAndChatRoomId(userId, chatRoomId)
                .map { it.changeRole(ChatRoomRole.OWNER) }
                .flatMap { userChatRoomRepository.update(it) }
        )
            .assertNext { updated ->
                assertEquals(ChatRoomRole.OWNER, updated.role)
                assertEquals(userId, updated.userId)
                assertEquals(chatRoomId, updated.chatRoomId)
            }
            .verifyComplete()

        // Then - Verify the update
        StepVerifier.create(userChatRoomRepository.findByUserIdAndChatRoomId(userId, chatRoomId))
            .assertNext { relationship ->
                assertEquals(ChatRoomRole.OWNER, relationship.role)
            }
            .verifyComplete()
    }

    @Test
    fun `should delete relationship successfully`() {
        // Given
        val userId = 3L
        val chatRoomId = 1L

        // When
        StepVerifier.create(
            userChatRoomRepository.findByUserIdAndChatRoomId(userId, chatRoomId)
                .flatMap { userChatRoomRepository.delete(it) }
        )
            .verifyComplete()

        // Then - Verify deletion
        StepVerifier.create(userChatRoomRepository.findByUserIdAndChatRoomId(userId, chatRoomId))
            .verifyComplete()

        // Check total count decreased
        StepVerifier.create(userChatRoomRepository.countActiveParticipantsByChatRoomId(chatRoomId))
            .expectNext(2L) // Should be 2 now (was 3)
            .verifyComplete()
    }

    @Test
    fun `should delete by user id and chat room id`() {
        // Given
        val userId = 2L
        val chatRoomId = 1L

        // When
        StepVerifier.create(userChatRoomRepository.deleteByUserIdAndChatRoomId(userId, chatRoomId))
            .verifyComplete()

        // Then - Verify deletion
        StepVerifier.create(userChatRoomRepository.existsByUserIdAndChatRoomId(userId, chatRoomId))
            .expectNext(false)
            .verifyComplete()
    }

    @Test
    fun `should delete all relationships by chat room id`() {
        // Given
        val chatRoomId = 1L

        // When
        StepVerifier.create(userChatRoomRepository.deleteByChatRoomId(chatRoomId))
            .verifyComplete()

        // Then - Verify all relationships for the chat room are deleted
        StepVerifier.create(userChatRoomRepository.findByChatRoomId(chatRoomId))
            .verifyComplete()

        // But other chat rooms should still have relationships
        StepVerifier.create(userChatRoomRepository.findByChatRoomId(2L))
            .expectNextCount(1) // Chat room 2 should still have 1 relationship
            .verifyComplete()
    }

    @Test
    fun `should delete all relationships by user id`() {
        // Given
        val userId = 1L

        // When
        StepVerifier.create(userChatRoomRepository.deleteByUserId(userId))
            .verifyComplete()

        // Then - Verify all relationships for the user are deleted
        StepVerifier.create(userChatRoomRepository.findByUserId(userId))
            .verifyComplete()

        // But other users should still have relationships
        StepVerifier.create(userChatRoomRepository.findByUserId(2L))
            .expectNextCount(1) // User 2 should still have 1 relationship
            .verifyComplete()
    }

    @Test
    fun `should handle relationship with all possible states`() {
        // Given
        val complexRelationship = UserChatRoom(
            userId = 6L,
            chatRoomId = 3L,
            role = ChatRoomRole.ADMIN,
            joinedAt = LocalDateTime.now().minusDays(1),
            isActive = true,
            lastReadAt = LocalDateTime.now().minusHours(2),
            isMuted = true,
            isPinned = true,
            leftAt = null,
            invitedBy = 1L,
            updatedAt = LocalDateTime.now()
        )

        // When & Then
        StepVerifier.create(userChatRoomRepository.save(complexRelationship))
            .assertNext { saved ->
                assertEquals(6L, saved.userId)
                assertEquals(3L, saved.chatRoomId)
                assertEquals(ChatRoomRole.ADMIN, saved.role)
                assertTrue(saved.isActive)
                assertTrue(saved.isMuted)
                assertTrue(saved.isPinned)
                assertEquals(1L, saved.invitedBy)
                assertNotNull(saved.lastReadAt)
                assertNotNull(saved.joinedAt)
                assertNotNull(saved.updatedAt)
            }
            .verifyComplete()
    }

    @Test
    fun `should handle business logic integration correctly`() {
        // Given
        val userId = 1L
        val chatRoomId = 1L

        // When & Then - Test business logic methods
        StepVerifier.create(userChatRoomRepository.findByUserIdAndChatRoomId(userId, chatRoomId))
            .assertNext { relationship ->
                // Test domain business logic
                assertTrue(relationship.isValid())
                assertTrue(relationship.isActiveParticipant())
                assertFalse(relationship.hasLeft())
                assertTrue(relationship.isOwner())
                assertTrue(relationship.hasAdminPrivileges())
                assertTrue(relationship.canDeleteRoom())
                assertTrue(relationship.canModifyRoomSettings())
                assertTrue(relationship.canKickUser(ChatRoomRole.MEMBER))
                assertTrue(relationship.canChangeRoleOf(ChatRoomRole.ADMIN))
                assertTrue(relationship.getParticipationDays() >= 0)
            }
            .verifyComplete()
    }
}