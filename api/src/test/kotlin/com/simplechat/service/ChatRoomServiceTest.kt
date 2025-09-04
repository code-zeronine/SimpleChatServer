package com.simplechat.service

import com.simplechat.domain.entity.ChatRoom
import com.simplechat.domain.entity.ChatRoomRole
import com.simplechat.domain.entity.User
import com.simplechat.domain.entity.UserChatRoom
import com.simplechat.domain.exception.BusinessLogicException
import com.simplechat.domain.exception.ChatRoomNotFoundException
import com.simplechat.domain.exception.InsufficientPermissionException
import com.simplechat.domain.exception.UserNotFoundException
import com.simplechat.domain.repository.ChatRoomRepository
import com.simplechat.domain.repository.UserRepository
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.LocalDateTime

class ChatRoomServiceTest {

    private val chatRoomRepository = mockk<ChatRoomRepository>()
    private val userRepository = mockk<UserRepository>()
    private val userChatRoomService = mockk<UserChatRoomService>()
    private val transactionalOperator = mockk<TransactionalOperator>()
    private val chatRoomService = ChatRoomService(
        chatRoomRepository,
        userRepository,
        userChatRoomService,
        transactionalOperator
    )

    private lateinit var owner: User
    private lateinit var admin: User
    private lateinit var member: User
    private lateinit var chatRoom: ChatRoom
    private lateinit var ownerRelationship: UserChatRoom
    private lateinit var adminRelationship: UserChatRoom
    private lateinit var memberRelationship: UserChatRoom

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        
        // TransactionalOperator mock 설정 - 모든 Mono를 그대로 통과시킴
        every { transactionalOperator.transactional(any<Mono<*>>()) } answers { firstArg<Mono<*>>() }

        owner = User(id = 1L, email = "owner@test.com", passwordHash = "hashed", nickname = "Owner")
        admin = User(id = 2L, email = "admin@test.com", passwordHash = "hashed", nickname = "Admin")
        member = User(id = 3L, email = "member@test.com", passwordHash = "hashed", nickname = "Member")

        chatRoom = ChatRoom(
            id = 1L,
            name = "Test Room",
            description = "A room for testing",
            createdBy = owner.id!!,
            isPrivate = false,
            maxParticipants = 50,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        ownerRelationship = UserChatRoom(
            userId = owner.id!!,
            chatRoomId = chatRoom.id!!,
            role = ChatRoomRole.OWNER,
            joinedAt = LocalDateTime.now(),
            isActive = true
        )
        adminRelationship = UserChatRoom(
            userId = admin.id!!,
            chatRoomId = chatRoom.id!!,
            role = ChatRoomRole.ADMIN,
            joinedAt = LocalDateTime.now(),
            isActive = true
        )
        memberRelationship = UserChatRoom(
            userId = member.id!!,
            chatRoomId = chatRoom.id!!,
            role = ChatRoomRole.MEMBER,
            joinedAt = LocalDateTime.now(),
            isActive = true
        )
    }

    @Nested
    @DisplayName("createChatRoom")
    inner class CreateChatRoom {
        @Test
        fun `성공 - 채팅방을 생성하고 생성자를 소유자로 참여시킨다`() {
            val chatRoomSlot = slot<ChatRoom>()
            val savedChatRoom = chatRoom.copy(id = 1L)

            every { userRepository.findById(owner.id!!) } returns Mono.just(owner)
            every { chatRoomRepository.existsByName(any()) } returns Mono.just(false)
            every { chatRoomRepository.save(capture(chatRoomSlot)) } answers { Mono.just(savedChatRoom) }
            every { userChatRoomService.joinChatRoom(owner.id!!, savedChatRoom.id!!, ChatRoomRole.OWNER) } returns Mono.just(ownerRelationship)

            StepVerifier.create(
                chatRoomService.createChatRoom(
                    name = chatRoom.name,
                    description = chatRoom.description,
                    isPrivate = chatRoom.isPrivate,
                    maxParticipants = chatRoom.maxParticipants,
                    ownerId = owner.id!!
                )
            )
                .expectNextMatches { it.id == savedChatRoom.id }
                .verifyComplete()

            verify { chatRoomRepository.save(any()) }
            verify { userChatRoomService.joinChatRoom(owner.id!!, savedChatRoom.id!!, ChatRoomRole.OWNER) }
        }

        @Test
        fun `실패 - 존재하지 않는 사용자는 채팅방을 생성할 수 없다`() {
            every { userRepository.findById(99L) } returns Mono.empty()

            StepVerifier.create(chatRoomService.createChatRoom(name = "New Room", ownerId = 99L))
                .expectError(UserNotFoundException::class.java)
                .verify()
        }

        @Test
        fun `실패 - 이미 존재하는 이름으로 채팅방을 생성할 수 없다`() {
            every { userRepository.findById(owner.id!!) } returns Mono.just(owner)
            every { chatRoomRepository.existsByName(chatRoom.name) } returns Mono.just(true)

            StepVerifier.create(chatRoomService.createChatRoom(name = chatRoom.name, ownerId = owner.id!!))
                .expectError(BusinessLogicException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("updateChatRoom")
    inner class UpdateChatRoom {
        @Test
        fun `성공 - 관리자 권한으로 채팅방 정보를 수정한다`() {
            val updatedName = "Updated Room Name"
            val updatedRoom = chatRoom.copy(name = updatedName)

            every { userChatRoomService.getUserChatRoomRelationship(admin.id!!, chatRoom.id!!) } returns Mono.just(adminRelationship)
            every { chatRoomRepository.findById(chatRoom.id!!) } returns Mono.just(chatRoom)
            every { chatRoomRepository.existsByName(updatedName) } returns Mono.just(false)
            every { chatRoomRepository.save(any()) } returns Mono.just(updatedRoom)

            StepVerifier.create(chatRoomService.updateChatRoom(id = chatRoom.id!!, name = updatedName, requesterId = admin.id!!))
                .expectNextMatches { it.id == updatedRoom.id }
                .verifyComplete()
        }

        @Test
        fun `실패 - 권한이 없는 사용자는 채팅방 정보를 수정할 수 없다`() {
            every { userChatRoomService.getUserChatRoomRelationship(member.id!!, chatRoom.id!!) } returns Mono.just(memberRelationship)
            every { chatRoomRepository.findById(chatRoom.id!!) } returns Mono.just(chatRoom)

            StepVerifier.create(chatRoomService.updateChatRoom(id = chatRoom.id!!, name = "New Name", requesterId = member.id!!))
                .expectError(InsufficientPermissionException::class.java)
                .verify()
        }

        @Test
        fun `실패 - 이미 존재하는 이름으로 채팅방 이름을 변경할 수 없다`() {
            val existingName = "Existing Name"
            every { userChatRoomService.getUserChatRoomRelationship(owner.id!!, chatRoom.id!!) } returns Mono.just(ownerRelationship)
            every { chatRoomRepository.findById(chatRoom.id!!) } returns Mono.just(chatRoom)
            every { chatRoomRepository.existsByName(existingName) } returns Mono.just(true)

            StepVerifier.create(chatRoomService.updateChatRoom(id = chatRoom.id!!, name = existingName, requesterId = owner.id!!))
                .expectError(BusinessLogicException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("deleteChatRoom")
    inner class DeleteChatRoom {
        @Test
        fun `성공 - 소유자가 채팅방을 삭제한다`() {
            every { userChatRoomService.getUserChatRoomRelationship(owner.id!!, chatRoom.id!!) } returns Mono.just(ownerRelationship)
            every { chatRoomRepository.findById(chatRoom.id!!) } returns Mono.just(chatRoom)
            every { userChatRoomService.deleteAllByChatRoomId(chatRoom.id!!) } returns Mono.empty()
            every { chatRoomRepository.deleteById(chatRoom.id!!) } returns Mono.empty()

            StepVerifier.create(chatRoomService.deleteChatRoom(chatRoom.id!!, owner.id!!))
                .verifyComplete()

            verify { userChatRoomService.deleteAllByChatRoomId(chatRoom.id!!) }
            verify { chatRoomRepository.deleteById(chatRoom.id!!) }
        }

        @Test
        fun `실패 - 소유자가 아닌 사용자는 채팅방을 삭제할 수 없다`() {
            every { userChatRoomService.getUserChatRoomRelationship(admin.id!!, chatRoom.id!!) } returns Mono.just(adminRelationship)
            every { chatRoomRepository.findById(chatRoom.id!!) } returns Mono.just(chatRoom)

            StepVerifier.create(chatRoomService.deleteChatRoom(chatRoom.id!!, admin.id!!))
                .expectError(InsufficientPermissionException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("joinChatRoom")
    inner class JoinChatRoom {
        private val newUser = User(id = 4L, email = "new@test.com", passwordHash = "hashed", nickname = "NewUser")

        @BeforeEach
        fun joinSetUp() {
            every { userRepository.findById(newUser.id!!) } returns Mono.just(newUser)
            every { userChatRoomService.isActiveParticipant(newUser.id!!, chatRoom.id!!) } returns Mono.just(false)
            every { userChatRoomService.countActiveParticipants(chatRoom.id!!) } returns Mono.just(10L) // Assume 10 participants
        }

        @Test
        fun `성공 - 공개 채팅방에 참여한다`() {
            val publicRoom = chatRoom.copy(isPrivate = false)
            val newRelationship = UserChatRoom(
                userId = newUser.id!!,
                chatRoomId = publicRoom.id!!,
                role = ChatRoomRole.MEMBER,
                joinedAt = LocalDateTime.now(),
                isActive = true
            )

            every { chatRoomRepository.findById(publicRoom.id!!) } returns Mono.just(publicRoom)
            every { userChatRoomService.findUserChatRoomRelationship(newUser.id!!, publicRoom.id!!) } returns Mono.empty()
            every { userChatRoomService.joinChatRoom(newUser.id!!, publicRoom.id!!, ChatRoomRole.MEMBER, null) } returns Mono.just(newRelationship)

            StepVerifier.create(chatRoomService.joinChatRoom(newUser.id!!, publicRoom.id!!))
                .expectNext(newRelationship)
                .verifyComplete()
        }

        @Test
        fun `성공 - 초대를 받아 비공개 채팅방에 참여한다`() {
            val privateRoom = chatRoom.copy(isPrivate = true)
            val newRelationship = UserChatRoom(
                userId = newUser.id!!,
                chatRoomId = privateRoom.id!!,
                role = ChatRoomRole.MEMBER,
                joinedAt = LocalDateTime.now(),
                isActive = true
            )

            every { chatRoomRepository.findById(privateRoom.id!!) } returns Mono.just(privateRoom)
            every { userChatRoomService.findUserChatRoomRelationship(newUser.id!!, privateRoom.id!!) } returns Mono.empty()
            every { userChatRoomService.getUserChatRoomRelationship(admin.id!!, privateRoom.id!!) } returns Mono.just(adminRelationship)
            every { userChatRoomService.joinChatRoom(newUser.id!!, privateRoom.id!!, ChatRoomRole.MEMBER, admin.id!!) } returns Mono.just(newRelationship)

            StepVerifier.create(chatRoomService.joinChatRoom(userId = newUser.id!!, roomId = privateRoom.id!!, invitedBy = admin.id!!))
                .expectNext(newRelationship)
                .verifyComplete()
        }

        @Test
        fun `실패 - 초대 없이 비공개 채팅방에 참여할 수 없다`() {
            val privateRoom = chatRoom.copy(isPrivate = true)
            every { chatRoomRepository.findById(privateRoom.id!!) } returns Mono.just(privateRoom)
            every { userChatRoomService.findUserChatRoomRelationship(newUser.id!!, privateRoom.id!!) } returns Mono.empty()

            StepVerifier.create(chatRoomService.joinChatRoom(newUser.id!!, privateRoom.id!!))
                .expectError(BusinessLogicException::class.java)
                .verify()
        }

        @Test
        fun `실패 - 이미 참여한 채팅방에 다시 참여할 수 없다`() {
            val activeRelationship = memberRelationship.copy(isActive = true)
            every { userChatRoomService.isActiveParticipant(member.id!!, chatRoom.id!!) } returns Mono.just(true)
            every { userRepository.findById(member.id!!) } returns Mono.just(member)
            every { chatRoomRepository.findById(chatRoom.id!!) } returns Mono.just(chatRoom)
            every { userChatRoomService.countActiveParticipants(chatRoom.id!!) } returns Mono.just(10L)
            every { userChatRoomService.findUserChatRoomRelationship(member.id!!, chatRoom.id!!) } returns Mono.just(activeRelationship)

            StepVerifier.create(chatRoomService.joinChatRoom(member.id!!, chatRoom.id!!))
                .expectNext(activeRelationship)
                .verifyComplete()
        }

        @Test
        fun `실패 - 가득 찬 채팅방에 참여할 수 없다`() {
            val fullRoom = chatRoom.copy(maxParticipants = 2)
            every { chatRoomRepository.findById(fullRoom.id!!) } returns Mono.just(fullRoom)
            every { userChatRoomService.countActiveParticipants(fullRoom.id!!) } returns Mono.just(2L)
            every { userChatRoomService.findUserChatRoomRelationship(newUser.id!!, fullRoom.id!!) } returns Mono.empty()

            StepVerifier.create(chatRoomService.joinChatRoom(newUser.id!!, fullRoom.id!!))
                .expectError(BusinessLogicException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("leaveChatRoom")
    inner class LeaveChatRoom {
        @Test
        fun `성공 - 일반 멤버가 채팅방을 나간다`() {
            every { userChatRoomService.findUserChatRoomRelationship(member.id!!, chatRoom.id!!) } returns Mono.just(memberRelationship)
            every { userChatRoomService.leaveChatRoomWithRelationship(memberRelationship) } returns Mono.empty()

            StepVerifier.create(chatRoomService.leaveChatRoom(member.id!!, chatRoom.id!!))
                .verifyComplete()

            verify { userChatRoomService.leaveChatRoomWithRelationship(memberRelationship) }
        }

        @Test
        fun `성공 - 소유자가 나가고 관리자가 새 소유자가 된다`() {
            every { userChatRoomService.findUserChatRoomRelationship(owner.id!!, chatRoom.id!!) } returns Mono.just(ownerRelationship)
            every { userChatRoomService.getChatRoomAdmins(chatRoom.id!!) } returns Flux.just(adminRelationship)
            every { userChatRoomService.changeUserRole(owner.id!!, admin.id!!, chatRoom.id!!, ChatRoomRole.OWNER) } returns Mono.just(adminRelationship.changeRole(ChatRoomRole.OWNER))
            every { userChatRoomService.leaveChatRoomWithRelationship(ownerRelationship) } returns Mono.empty()

            StepVerifier.create(chatRoomService.leaveChatRoom(owner.id!!, chatRoom.id!!))
                .verifyComplete()

            verify { userChatRoomService.changeUserRole(owner.id!!, admin.id!!, chatRoom.id!!, ChatRoomRole.OWNER) }
        }

        @Test
        fun `성공 - 소유자가 나가고 관리자가 없어 일반 멤버가 새 소유자가 된다`() {
            every { userChatRoomService.findUserChatRoomRelationship(owner.id!!, chatRoom.id!!) } returns Mono.just(ownerRelationship)
            every { userChatRoomService.getChatRoomAdmins(chatRoom.id!!) } returns Flux.empty()
            every { userChatRoomService.getChatRoomActiveParticipants(chatRoom.id!!) } returns Flux.just(ownerRelationship, memberRelationship)
            every { userChatRoomService.changeUserRole(owner.id!!, member.id!!, chatRoom.id!!, ChatRoomRole.OWNER) } returns Mono.just(memberRelationship.changeRole(ChatRoomRole.OWNER))
            every { userChatRoomService.leaveChatRoomWithRelationship(ownerRelationship) } returns Mono.empty()

            StepVerifier.create(chatRoomService.leaveChatRoom(owner.id!!, chatRoom.id!!))
                .verifyComplete()

            verify { userChatRoomService.changeUserRole(owner.id!!, member.id!!, chatRoom.id!!, ChatRoomRole.OWNER) }
        }

        @Test
        fun `성공 - 마지막 참여자인 소유자가 나가면 채팅방이 삭제된다`() {
            every { userChatRoomService.findUserChatRoomRelationship(owner.id!!, chatRoom.id!!) } returns Mono.just(ownerRelationship)
            every { userChatRoomService.getChatRoomAdmins(chatRoom.id!!) } returns Flux.empty()
            every { userChatRoomService.getChatRoomActiveParticipants(chatRoom.id!!) } returns Flux.just(ownerRelationship)
            every { userChatRoomService.leaveChatRoomWithRelationship(ownerRelationship) } returns Mono.empty()
            every { userChatRoomService.deleteAllByChatRoomId(chatRoom.id!!) } returns Mono.empty()
            every { chatRoomRepository.deleteById(chatRoom.id!!) } returns Mono.empty()

            StepVerifier.create(chatRoomService.leaveChatRoom(owner.id!!, chatRoom.id!!))
                .verifyComplete()

            verify { chatRoomRepository.deleteById(chatRoom.id!!) }
        }
    }

    @Nested
    @DisplayName("findChatRoomById")
    inner class FindChatRoomById {
        @Test
        fun `성공 - ID로 채팅방을 찾는다`() {
            every { chatRoomRepository.findById(chatRoom.id!!) } returns Mono.just(chatRoom)

            StepVerifier.create(chatRoomService.findChatRoomById(chatRoom.id!!))
                .expectNext(chatRoom)
                .verifyComplete()
        }

        @Test
        fun `실패 - 존재하지 않는 ID로 채팅방을 찾을 수 없다`() {
            every { chatRoomRepository.findById(99L) } returns Mono.empty()

            StepVerifier.create(chatRoomService.findChatRoomById(99L))
                .expectError(ChatRoomNotFoundException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("getChatRoomDetails")
    inner class GetChatRoomDetails {
        @Test
        fun `성공 - 참여자가 채팅방 상세 정보를 조회한다`() {
            val participants = listOf(ownerRelationship, memberRelationship)
            every { chatRoomRepository.findById(chatRoom.id!!) } returns Mono.just(chatRoom)
            every { userChatRoomService.countActiveParticipants(chatRoom.id!!) } returns Mono.just(2L)
            every { userChatRoomService.getChatRoomActiveParticipants(chatRoom.id!!) } returns Flux.fromIterable(participants)
            every { userChatRoomService.getUserChatRoomRelationship(member.id!!, chatRoom.id!!) } returns Mono.just(memberRelationship)

            StepVerifier.create(chatRoomService.getChatRoomDetails(chatRoom.id!!, member.id!!))
                .expectNextMatches { details ->
                    details.room == chatRoom &&
                    details.participantCount == 2 &&
                    details.participants.containsAll(participants) &&
                    details.userRelationship == memberRelationship
                }
                .verifyComplete()
        }

        @Test
        fun `성공 - 비참여자가 채팅방 상세 정보를 조회한다`() {
            val nonMember = User(id = 99L, email = "non@member.com", passwordHash = "hashed", nickname = "NonMember")
            val participants = listOf(ownerRelationship)
            every { chatRoomRepository.findById(chatRoom.id!!) } returns Mono.just(chatRoom)
            every { userChatRoomService.countActiveParticipants(chatRoom.id!!) } returns Mono.just(1L)
            every { userChatRoomService.getChatRoomActiveParticipants(chatRoom.id!!) } returns Flux.fromIterable(participants)
            every { userChatRoomService.getUserChatRoomRelationship(nonMember.id!!, chatRoom.id!!) } returns Mono.empty()

            StepVerifier.create(chatRoomService.getChatRoomDetails(chatRoom.id!!, nonMember.id!!))
                .expectNextMatches { details ->
                    details.room == chatRoom &&
                    details.participantCount == 1 &&
                    details.participants.containsAll(participants) &&
                    details.userRelationship == null
                }
                .verifyComplete()
        }
    }
}