package com.simplechat.infrastructure.repository

import com.simplechat.domain.entity.User
import com.simplechat.infrastructure.repository.ChatRoomRepository
import com.simplechat.infrastructure.repository.UserChatRoomRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import reactor.test.StepVerifier
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@SpringBootApplication
class InfrastructureRepositoryTestApplication

@SpringBootTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var chatRoomRepository: ChatRoomRepository

    @Autowired
    private lateinit var userChatRoomRepository: UserChatRoomRepository

    private val testUsers = listOf(
        User(
            email = "john.doe@example.com",
            passwordHash = "\$2a\$10\$hashedPassword1",
            nickname = "JohnDoe",
            createdAt = LocalDateTime.now().minusDays(5)
        ),
        User(
            email = "jane.smith@example.com", 
            passwordHash = "\$2a\$10\$hashedPassword2",
            nickname = "JaneSmith",
            createdAt = LocalDateTime.now().minusDays(3)
        ),
        User(
            email = "bob.wilson@example.com",
            passwordHash = "\$2a\$10\$hashedPassword3", 
            nickname = "BobWilson",
            createdAt = LocalDateTime.now().minusDays(1)
        )
    )

    @BeforeEach
    fun setUp() {
        // 테스트 전 데이터 정리 (의존성 역순으로)
        StepVerifier.create(userChatRoomRepository.deleteAll())
            .verifyComplete()
        StepVerifier.create(chatRoomRepository.deleteAll())
            .verifyComplete()
        StepVerifier.create(userRepository.deleteAll())
            .verifyComplete()

        // 테스트 데이터 삽입
        StepVerifier.create(userRepository.saveAll(testUsers))
            .expectNextCount(3)
            .verifyComplete()
    }

    @Test
    fun `should save user successfully`() {
        // Given
        val newUser = User(
            email = "new.user@example.com",
            passwordHash = "\$2a\$10\$newHashedPassword",
            nickname = "NewUser"
        )

        // When & Then
        StepVerifier.create(userRepository.save(newUser))
            .assertNext { savedUser ->
                assertNotNull(savedUser.id)
                assertEquals(newUser.email, savedUser.email)
                assertEquals(newUser.nickname, savedUser.nickname)
                assertEquals(newUser.passwordHash, savedUser.passwordHash)
                assertNotNull(savedUser.createdAt)
            }
            .verifyComplete()
    }

    @Test
    fun `should find user by email`() {
        // Given
        val expectedEmail = "john.doe@example.com"

        // When & Then
        StepVerifier.create(userRepository.findByEmail(expectedEmail))
            .assertNext { user ->
                assertEquals(expectedEmail, user.email)
                assertEquals("JohnDoe", user.nickname)
            }
            .verifyComplete()
    }

    @Test
    fun `should return empty mono when email not found`() {
        // Given
        val nonExistentEmail = "nonexistent@example.com"

        // When & Then
        StepVerifier.create(userRepository.findByEmail(nonExistentEmail))
            .verifyComplete()
    }

    @Test
    fun `should find user by nickname`() {
        // Given
        val expectedNickname = "JaneSmith"

        // When & Then
        StepVerifier.create(userRepository.findByNickname(expectedNickname))
            .assertNext { user ->
                assertEquals("jane.smith@example.com", user.email)
                assertEquals(expectedNickname, user.nickname)
            }
            .verifyComplete()
    }

    @Test
    fun `should check if email exists`() {
        // Given
        val existingEmail = "john.doe@example.com"
        val nonExistentEmail = "nonexistent@example.com"

        // When & Then - 존재하는 이메일
        StepVerifier.create(userRepository.existsByEmail(existingEmail))
            .expectNext(true)
            .verifyComplete()

        // When & Then - 존재하지 않는 이메일
        StepVerifier.create(userRepository.existsByEmail(nonExistentEmail))
            .expectNext(false)
            .verifyComplete()
    }

    @Test
    fun `should check if nickname exists`() {
        // Given
        val existingNickname = "JohnDoe"
        val nonExistentNickname = "NonExistentUser"

        // When & Then - 존재하는 닉네임
        StepVerifier.create(userRepository.existsByNickname(existingNickname))
            .expectNext(true)
            .verifyComplete()

        // When & Then - 존재하지 않는 닉네임
        StepVerifier.create(userRepository.existsByNickname(nonExistentNickname))
            .expectNext(false)
            .verifyComplete()
    }

    @Test
    fun `should find user by email or nickname`() {
        // Given
        val email = "john.doe@example.com"
        val nickname = "JaneSmith"

        // When & Then - 이메일로 조회
        StepVerifier.create(userRepository.findByEmailOrNickname(email, "nonexistent"))
            .assertNext { user ->
                assertEquals(email, user.email)
                assertEquals("JohnDoe", user.nickname)
            }
            .verifyComplete()

        // When & Then - 닉네임으로 조회
        StepVerifier.create(userRepository.findByEmailOrNickname("nonexistent@example.com", nickname))
            .assertNext { user ->
                assertEquals("jane.smith@example.com", user.email)
                assertEquals(nickname, user.nickname)
            }
            .verifyComplete()
    }

    @Test
    fun `should find recent users with limit`() {
        // Given
        val limit = 2

        // When & Then
        StepVerifier.create(userRepository.findRecentUsers(limit))
            .assertNext { user ->
                assertEquals("BobWilson", user.nickname) // 가장 최근 사용자
            }
            .assertNext { user ->
                assertEquals("JaneSmith", user.nickname) // 두 번째 최근 사용자
            }
            .verifyComplete()
    }

    @Test
    fun `should find users created after specific date`() {
        // Given
        val afterDate = LocalDateTime.now().minusDays(2).toLocalDate().toString()

        // When & Then
        StepVerifier.create(userRepository.findUsersCreatedAfter(afterDate))
            .expectNextCount(1) // BobWilson만 1일 전에 생성됨
            .verifyComplete()
    }

    @Test
    fun `should count total users`() {
        // When & Then
        StepVerifier.create(userRepository.countUsers())
            .expectNext(3L) // 테스트 데이터 3개
            .verifyComplete()
    }

    @Test
    fun `should update user information`() {
        // Given
        val originalEmail = "john.doe@example.com"

        // When - 사용자 조회 후 수정
        StepVerifier.create(
            userRepository.findByEmail(originalEmail)
                .flatMap { user ->
                    val updatedUser = user.copy(nickname = "UpdatedJohn")
                    userRepository.save(updatedUser)
                }
        )
            .assertNext { updatedUser ->
                assertEquals(originalEmail, updatedUser.email)
                assertEquals("UpdatedJohn", updatedUser.nickname)
            }
            .verifyComplete()

        // Then - 업데이트된 정보 확인
        StepVerifier.create(userRepository.findByEmail(originalEmail))
            .assertNext { user ->
                assertEquals("UpdatedJohn", user.nickname)
            }
            .verifyComplete()
    }

    @Test
    fun `should delete user successfully`() {
        // Given
        val emailToDelete = "john.doe@example.com"

        // When
        StepVerifier.create(
            userRepository.findByEmail(emailToDelete)
                .flatMap { user ->
                    userRepository.delete(user)
                }
        )
            .verifyComplete()

        // Then - 삭제된 사용자가 조회되지 않음을 확인
        StepVerifier.create(userRepository.findByEmail(emailToDelete))
            .verifyComplete()

        // 전체 사용자 수 확인
        StepVerifier.create(userRepository.countUsers())
            .expectNext(2L) // 1명 삭제되어 2명 남음
            .verifyComplete()
    }
}