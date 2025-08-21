package com.simplechat.domain.entity

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import jakarta.validation.Validation
import jakarta.validation.Validator
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import java.time.LocalDateTime

class UserTest {

    private val validator: Validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `should create valid user with all required fields`() {
        // Given
        val user = User(
            id = 1L,
            email = "test@example.com",
            passwordHash = "hashedPassword123",
            nickname = "testuser",
            createdAt = LocalDateTime.now()
        )

        // When
        val violations = validator.validate(user)

        // Then
        assertTrue(violations.isEmpty(), "Valid user should have no validation violations")
        assertEquals("test@example.com", user.email)
        assertEquals("hashedPassword123", user.passwordHash)
        assertEquals("testuser", user.nickname)
    }

    @Test
    fun `should fail validation for invalid email`() {
        // Given
        val user = User(
            email = "invalid-email",
            passwordHash = "hashedPassword123",
            nickname = "testuser"
        )

        // When
        val violations = validator.validate(user)

        // Then
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.message.contains("유효한 이메일") })
    }

    @Test
    fun `should fail validation for blank email`() {
        // Given
        val user = User(
            email = "",
            passwordHash = "hashedPassword123",
            nickname = "testuser"
        )

        // When
        val violations = validator.validate(user)

        // Then
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.message.contains("이메일은 필수") })
    }

    @Test
    fun `should fail validation for blank password hash`() {
        // Given
        val user = User(
            email = "test@example.com",
            passwordHash = "",
            nickname = "testuser"
        )

        // When
        val violations = validator.validate(user)

        // Then
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.message.contains("비밀번호는 필수") })
    }

    @Test
    fun `should fail validation for blank nickname`() {
        // Given
        val user = User(
            email = "test@example.com",
            passwordHash = "hashedPassword123",
            nickname = ""
        )

        // When
        val violations = validator.validate(user)

        // Then
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.message.contains("닉네임은 필수") })
    }

    @Test
    fun `should fail validation for nickname too short`() {
        // Given
        val user = User(
            email = "test@example.com",
            passwordHash = "hashedPassword123",
            nickname = "a"
        )

        // When
        val violations = validator.validate(user)

        // Then
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.message.contains("2자 이상 50자 이하") })
    }

    @Test
    fun `should fail validation for nickname too long`() {
        // Given
        val user = User(
            email = "test@example.com",
            passwordHash = "hashedPassword123",
            nickname = "a".repeat(51)
        )

        // When
        val violations = validator.validate(user)

        // Then
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.message.contains("2자 이상 50자 이하") })
    }

    @Test
    fun `should create user with default created at time`() {
        // Given & When
        val before = LocalDateTime.now()
        val user = User(
            email = "test@example.com",
            passwordHash = "hashedPassword123",
            nickname = "testuser"
        )
        val after = LocalDateTime.now()

        // Then
        assertTrue(user.createdAt.isAfter(before) || user.createdAt.isEqual(before))
        assertTrue(user.createdAt.isBefore(after) || user.createdAt.isEqual(after))
    }

    @Test
    fun `should implement equals correctly based on id`() {
        // Given
        val user1 = User(
            id = 1L,
            email = "test1@example.com",
            passwordHash = "hash1",
            nickname = "user1"
        )
        val user2 = User(
            id = 1L,
            email = "test2@example.com",
            passwordHash = "hash2",
            nickname = "user2"
        )
        val user3 = User(
            id = 2L,
            email = "test1@example.com",
            passwordHash = "hash1",
            nickname = "user1"
        )

        // Then
        assertEquals(user1, user2) // Same ID
        assertNotEquals(user1, user3) // Different ID
    }

    @Test
    fun `should implement hashCode correctly based on id`() {
        // Given
        val user1 = User(
            id = 1L,
            email = "test1@example.com",
            passwordHash = "hash1",
            nickname = "user1"
        )
        val user2 = User(
            id = 1L,
            email = "test2@example.com",
            passwordHash = "hash2",
            nickname = "user2"
        )

        // Then
        assertEquals(user1.hashCode(), user2.hashCode()) // Same ID should have same hashCode
    }

    @Test
    fun `should handle null id in equals and hashCode`() {
        // Given
        val user1 = User(
            email = "test1@example.com",
            passwordHash = "hash1",
            nickname = "user1"
        )
        val user2 = User(
            email = "test1@example.com",
            passwordHash = "hash1",
            nickname = "user1"
        )

        // Then
        assertNotEquals(user1, user2) // Different instances with null ID should not be equal
        assertEquals(0, user1.hashCode()) // Null ID should return 0 hashCode
    }

    @Test
    fun `should not expose password hash in toString`() {
        // Given
        val user = User(
            id = 1L,
            email = "test@example.com",
            passwordHash = "secretPasswordHash",
            nickname = "testuser"
        )

        // When
        val toString = user.toString()

        // Then
        assertFalse(toString.contains("secretPasswordHash"), "toString should not expose password hash")
        assertTrue(toString.contains("test@example.com"))
        assertTrue(toString.contains("testuser"))
    }
}