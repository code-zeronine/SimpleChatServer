package com.simplechat.service

import com.simplechat.domain.entity.ChatRoom
import com.simplechat.domain.entity.ChatRoomRole
import com.simplechat.domain.entity.User
import com.simplechat.domain.entity.UserChatRoom
import com.simplechat.infrastructure.exception.InsufficientPermissionException
import com.simplechat.infrastructure.exception.UserChatRoomNotFoundException
import com.simplechat.domain.repository.ChatRoomRepository
import com.simplechat.domain.repository.UserRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.LocalDateTime

@DisplayName("ChatRoomService - 권한 검증 및 참여자 관리 테스트")
class ChatRoomPermissionManagementTest {

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
    private lateinit var outsider: User
    private lateinit var chatRoom: ChatRoom
    private lateinit var ownerRelationship: UserChatRoom
    private lateinit var adminRelationship: UserChatRoom
    private lateinit var memberRelationship: UserChatRoom

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        
        // TransactionalOperator mock 설정
        every { transactionalOperator.transactional(any<Mono<*>>()) } answers { firstArg<Mono<*>>() }

        owner = User(id = 1L, email = "owner@test.com", passwordHash = "hashed", nickname = "Owner")
        admin = User(id = 2L, email = "admin@test.com", passwordHash = "hashed", nickname = "Admin")
        member = User(id = 3L, email = "member@test.com", passwordHash = "hashed", nickname = "Member")
        outsider = User(id = 4L, email = "outsider@test.com", passwordHash = "hashed", nickname = "Outsider")

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
    @DisplayName("getParticipants")
    inner class GetParticipants {
        @Test
        fun `성공 - 참여자가 참여자 목록을 조회한다`() {
            val participants = listOf(ownerRelationship, adminRelationship, memberRelationship)
            
            every { userChatRoomService.getUserChatRoomRelationship(member.id!!, chatRoom.id!!) } returns Mono.just(memberRelationship)
            every { userChatRoomService.getChatRoomActiveParticipants(chatRoom.id!!) } returns Flux.fromIterable(participants)

            StepVerifier.create(chatRoomService.getParticipants(chatRoom.id!!, member.id!!))
                .expectNext(ownerRelationship)
                .expectNext(adminRelationship)
                .expectNext(memberRelationship)
                .verifyComplete()
        }

        @Test
        fun `실패 - 비참여자는 참여자 목록을 조회할 수 없다`() {
            every { userChatRoomService.getUserChatRoomRelationship(outsider.id!!, chatRoom.id!!) } returns 
                Mono.error(UserChatRoomNotFoundException("사용자-채팅방 관계를 찾을 수 없습니다."))

            StepVerifier.create(chatRoomService.getParticipants(chatRoom.id!!, outsider.id!!))
                .expectError(UserChatRoomNotFoundException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("changeParticipantRole")
    inner class ChangeParticipantRole {
        @Test
        fun `성공 - 소유자가 멤버를 관리자로 승격시킨다`() {
            val updatedRelationship = memberRelationship.changeRole(ChatRoomRole.ADMIN)
            
            every { userChatRoomService.getUserChatRoomRelationship(owner.id!!, chatRoom.id!!) } returns Mono.just(ownerRelationship)
            every { userChatRoomService.changeUserRole(owner.id!!, member.id!!, chatRoom.id!!, ChatRoomRole.ADMIN) } returns Mono.just(updatedRelationship)

            StepVerifier.create(chatRoomService.changeParticipantRole(owner.id!!, member.id!!, chatRoom.id!!, ChatRoomRole.ADMIN))
                .expectNext(updatedRelationship)
                .verifyComplete()
        }

        @Test
        fun `실패 - 일반 멤버는 다른 사용자의 역할을 변경할 수 없다`() {
            every { userChatRoomService.getUserChatRoomRelationship(member.id!!, chatRoom.id!!) } returns Mono.just(memberRelationship)

            StepVerifier.create(chatRoomService.changeParticipantRole(member.id!!, admin.id!!, chatRoom.id!!, ChatRoomRole.MEMBER))
                .expectError(InsufficientPermissionException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("kickParticipant")
    inner class KickParticipant {
        @Test
        fun `성공 - 관리자가 일반 멤버를 추방한다`() {
            every { userChatRoomService.getUserChatRoomRelationship(admin.id!!, chatRoom.id!!) } returns Mono.just(adminRelationship)
            every { userChatRoomService.kickUserFromChatRoom(admin.id!!, member.id!!, chatRoom.id!!) } returns Mono.empty()

            StepVerifier.create(chatRoomService.kickParticipant(admin.id!!, member.id!!, chatRoom.id!!))
                .verifyComplete()
        }

        @Test
        fun `실패 - 일반 멤버는 다른 사용자를 추방할 수 없다`() {
            every { userChatRoomService.getUserChatRoomRelationship(member.id!!, chatRoom.id!!) } returns Mono.just(memberRelationship)

            StepVerifier.create(chatRoomService.kickParticipant(member.id!!, admin.id!!, chatRoom.id!!))
                .expectError(InsufficientPermissionException::class.java)
                .verify()
        }
    }

    @Nested
    @DisplayName("getChatRoomAdmins")
    inner class GetChatRoomAdmins {
        @Test
        fun `성공 - 참여자가 관리자 목록을 조회한다`() {
            val admins = listOf(ownerRelationship, adminRelationship)
            
            every { userChatRoomService.getUserChatRoomRelationship(member.id!!, chatRoom.id!!) } returns Mono.just(memberRelationship)
            every { userChatRoomService.getChatRoomAdmins(chatRoom.id!!) } returns Flux.fromIterable(admins)

            StepVerifier.create(chatRoomService.getChatRoomAdmins(chatRoom.id!!, member.id!!))
                .expectNext(ownerRelationship)
                .expectNext(adminRelationship)
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("getChatRoomOwner")
    inner class GetChatRoomOwner {
        @Test
        fun `성공 - 참여자가 채팅방 소유자 정보를 조회한다`() {
            every { userChatRoomService.getUserChatRoomRelationship(member.id!!, chatRoom.id!!) } returns Mono.just(memberRelationship)
            every { userChatRoomService.getChatRoomOwner(chatRoom.id!!) } returns Mono.just(ownerRelationship)

            StepVerifier.create(chatRoomService.getChatRoomOwner(chatRoom.id!!, member.id!!))
                .expectNext(ownerRelationship)
                .verifyComplete()
        }
    }

    @Nested
    @DisplayName("hasPermissionForAction")
    inner class HasPermissionForAction {
        @Test
        fun `성공 - 관리자 권한 체크 - 소유자는 관리자 권한을 가진다`() {
            every { userChatRoomService.getUserChatRoomRelationship(owner.id!!, chatRoom.id!!) } returns Mono.just(ownerRelationship)

            StepVerifier.create(chatRoomService.hasPermissionForAction(owner.id!!, chatRoom.id!!, ChatRoomRole.ADMIN))
                .expectNext(true)
                .verifyComplete()
        }

        @Test
        fun `성공 - 관리자 권한 체크 - 일반 멤버는 관리자 권한을 가지지 않는다`() {
            every { userChatRoomService.getUserChatRoomRelationship(member.id!!, chatRoom.id!!) } returns Mono.just(memberRelationship)

            StepVerifier.create(chatRoomService.hasPermissionForAction(member.id!!, chatRoom.id!!, ChatRoomRole.ADMIN))
                .expectNext(false)
                .verifyComplete()
        }

        @Test
        fun `성공 - 비참여자는 권한을 가지지 않는다`() {
            every { userChatRoomService.getUserChatRoomRelationship(outsider.id!!, chatRoom.id!!) } returns 
                Mono.error(UserChatRoomNotFoundException("사용자-채팅방 관계를 찾을 수 없습니다."))

            StepVerifier.create(chatRoomService.hasPermissionForAction(outsider.id!!, chatRoom.id!!, ChatRoomRole.MEMBER))
                .expectNext(false)
                .verifyComplete()
        }
    }
}