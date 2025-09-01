package com.simplechat.domain.entity

import jakarta.validation.Validation
import jakarta.validation.Validator
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ChatRoomTest {

    private val validator: Validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `should create valid chat room with all required fields`() {
        // Given
        val chatRoom = ChatRoom(
            id = 1L,
            name = "General Chat",
            description = "General discussion room",
            createdBy = 100L,
            isPrivate = false,
            maxParticipants = 50,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        // When
        val violations = validator.validate(chatRoom)

        // Then
        assertTrue(violations.isEmpty(), "Valid chat room should have no validation violations")
        assertEquals("General Chat", chatRoom.name)
        assertEquals("General discussion room", chatRoom.description)
        assertEquals(100L, chatRoom.createdBy)
        assertFalse(chatRoom.isPrivate)
        assertEquals(50, chatRoom.maxParticipants)
    }

    @Test
    fun `should create valid chat room with minimal fields`() {
        // Given
        val chatRoom = ChatRoom(
            name = "Test Room",
            createdBy = 1L
        )

        // When
        val violations = validator.validate(chatRoom)

        // Then
        assertTrue(violations.isEmpty())
        assertEquals("Test Room", chatRoom.name)
        assertEquals(null, chatRoom.description)
        assertEquals(1L, chatRoom.createdBy)
        assertFalse(chatRoom.isPrivate)
        assertEquals(100, chatRoom.maxParticipants) // default value
    }

    @Test
    fun `should fail validation for blank name`() {
        // Given
        val chatRoom = ChatRoom(
            name = "",
            createdBy = 1L
        )

        // When
        val violations = validator.validate(chatRoom)

        // Then
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.message.contains("채팅방 이름은 필수") })
    }

    @Test
    fun `should fail validation for name too long`() {
        // Given
        val chatRoom = ChatRoom(
            name = "a".repeat(101), // 101 characters
            createdBy = 1L
        )

        // When
        val violations = validator.validate(chatRoom)

        // Then
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.message.contains("1자 이상 100자 이하") })
    }

    @Test
    fun `should fail validation for description too long`() {
        // Given
        val chatRoom = ChatRoom(
            name = "Test Room",
            description = "a".repeat(501), // 501 characters
            createdBy = 1L
        )

        // When
        val violations = validator.validate(chatRoom)

        // Then
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.message.contains("500자 이하") })
    }

    @Test
    fun `should validate chat room with business rules`() {
        // Given
        val validRoom = ChatRoom(
            name = "Valid Room",
            description = "Valid description",
            createdBy = 1L,
            maxParticipants = 50
        )

        val invalidRoom = ChatRoom(
            name = "",
            description = "a".repeat(501),
            createdBy = 1L,
            maxParticipants = -1
        )

        // When & Then
        assertTrue(validRoom.isValid())
        assertFalse(invalidRoom.isValid())
    }

    @Test
    fun `should check ownership correctly`() {
        // Given
        val chatRoom = ChatRoom(
            name = "Test Room",
            createdBy = 100L
        )

        // When & Then
        assertTrue(chatRoom.isOwnedBy(100L))
        assertFalse(chatRoom.isOwnedBy(200L))
    }

    @Test
    fun `should check private room status`() {
        // Given
        val publicRoom = ChatRoom(
            name = "Public Room",
            createdBy = 1L,
            isPrivate = false
        )

        val privateRoom = ChatRoom(
            name = "Private Room",
            createdBy = 1L,
            isPrivate = true
        )

        // When & Then
        assertFalse(publicRoom.isPrivateRoom())
        assertTrue(privateRoom.isPrivateRoom())
    }

    @Test
    fun `should check capacity correctly`() {
        // Given
        val chatRoom = ChatRoom(
            name = "Test Room",
            createdBy = 1L,
            maxParticipants = 10
        )

        // When & Then
        assertFalse(chatRoom.isAtCapacity(5))
        assertFalse(chatRoom.isAtCapacity(9))
        assertTrue(chatRoom.isAtCapacity(10))
        assertTrue(chatRoom.isAtCapacity(15))
    }

    @Test
    fun `should return correct display name`() {
        // Given
        val normalRoom = ChatRoom(
            name = "Normal Room",
            createdBy = 1L
        )

        val emptyNameRoom = ChatRoom(
            name = "   ",
            createdBy = 1L
        )

        // When & Then
        assertEquals("Normal Room", normalRoom.getDisplayName())
        assertEquals("이름 없는 채팅방", emptyNameRoom.getDisplayName())
    }

    @Test
    fun `should update room info correctly`() {
        // Given
        val originalRoom = ChatRoom(
            name = "Original Room",
            description = "Original description",
            createdBy = 1L,
            maxParticipants = 50,
            createdAt = LocalDateTime.now().minusHours(1)
        )

        // When
        val updatedRoom = originalRoom.updateInfo(
            newName = "Updated Room",
            newDescription = "Updated description",
            newMaxParticipants = 100
        )

        // Then
        assertEquals("Updated Room", updatedRoom.name)
        assertEquals("Updated description", updatedRoom.description)
        assertEquals(100, updatedRoom.maxParticipants)
        assertTrue(updatedRoom.updatedAt.isAfter(originalRoom.updatedAt))
        assertEquals(originalRoom.createdBy, updatedRoom.createdBy)
        assertEquals(originalRoom.createdAt, updatedRoom.createdAt)
    }

    @Test
    fun `should update room info with partial updates`() {
        // Given
        val originalRoom = ChatRoom(
            name = "Original Room",
            description = "Original description",
            createdBy = 1L,
            maxParticipants = 50
        )

        // When - only update name
        val updatedRoom = originalRoom.updateInfo(newName = "New Name Only")

        // Then
        assertEquals("New Name Only", updatedRoom.name)
        assertEquals("Original description", updatedRoom.description)
        assertEquals(50, updatedRoom.maxParticipants)
    }

    @Test
    fun `should ignore invalid updates`() {
        // Given
        val originalRoom = ChatRoom(
            name = "Original Room",
            createdBy = 1L,
            maxParticipants = 50
        )

        // When - provide invalid values
        val updatedRoom = originalRoom.updateInfo(
            newName = "",  // blank name should be ignored
            newMaxParticipants = -1  // negative max participants should be ignored
        )

        // Then - should keep original values
        assertEquals("Original Room", updatedRoom.name)
        assertEquals(50, updatedRoom.maxParticipants)
    }

    @Test
    fun `should ignore out of range max participants`() {
        // Given
        val originalRoom = ChatRoom(
            name = "Test Room",
            createdBy = 1L,
            maxParticipants = 50
        )

        // When
        val tooLargeUpdate = originalRoom.updateInfo(newMaxParticipants = 2000)
        val negativeUpdate = originalRoom.updateInfo(newMaxParticipants = -5)

        // Then
        assertEquals(50, tooLargeUpdate.maxParticipants)
        assertEquals(50, negativeUpdate.maxParticipants)
    }

    @Test
    fun `should implement equals correctly based on id`() {
        // Given
        val room1 = ChatRoom(
            id = 1L,
            name = "Room A",
            createdBy = 100L
        )
        val room2 = ChatRoom(
            id = 1L,
            name = "Room B",
            createdBy = 200L
        )
        val room3 = ChatRoom(
            id = 2L,
            name = "Room A",
            createdBy = 100L
        )

        // Then
        assertEquals(room1, room2) // Same ID
        assertNotEquals(room1, room3) // Different ID
    }

    @Test
    fun `should implement hashCode correctly based on id`() {
        // Given
        val room1 = ChatRoom(
            id = 1L,
            name = "Room A",
            createdBy = 100L
        )
        val room2 = ChatRoom(
            id = 1L,
            name = "Room B",
            createdBy = 200L
        )

        // Then
        assertEquals(room1.hashCode(), room2.hashCode()) // Same ID should have same hashCode
    }

    @Test
    fun `should handle null id in equals and hashCode`() {
        // Given
        val room1 = ChatRoom(
            name = "Room A",
            createdBy = 100L
        )
        val room2 = ChatRoom(
            name = "Room A",
            createdBy = 100L
        )

        // Then
        assertNotEquals(room1, room2) // Different instances with null ID should not be equal
        assertEquals(0, room1.hashCode()) // Null ID should return 0 hashCode
    }

    @Test
    fun `should create room with default timestamps`() {
        // Given & When
        val before = LocalDateTime.now()
        val chatRoom = ChatRoom(
            name = "Test Room",
            createdBy = 1L
        )
        val after = LocalDateTime.now()

        // Then
        assertTrue(chatRoom.createdAt.isAfter(before) || chatRoom.createdAt.isEqual(before))
        assertTrue(chatRoom.createdAt.isBefore(after) || chatRoom.createdAt.isEqual(after))
        assertTrue(chatRoom.updatedAt.isAfter(before) || chatRoom.updatedAt.isEqual(before))
        assertTrue(chatRoom.updatedAt.isBefore(after) || chatRoom.updatedAt.isEqual(after))
    }

    @Test
    fun `should not expose sensitive information in toString`() {
        // Given
        val chatRoom = ChatRoom(
            id = 1L,
            name = "Secret Room",
            description = "Very secret description",
            createdBy = 100L,
            isPrivate = true
        )

        // When
        val toString = chatRoom.toString()

        // Then
        assertTrue(toString.contains("Secret Room"))
        assertTrue(toString.contains("100"))
        assertTrue(toString.contains("true"))
        assertFalse(toString.contains("Very secret description"), "toString should not expose description")
    }

    @Test
    fun `should validate edge cases for business rules`() {
        // Given & When & Then
        val maxParticipantsRoom = ChatRoom(
            name = "Max Room",
            createdBy = 1L,
            maxParticipants = 1000
        )
        assertTrue(maxParticipantsRoom.isValid())

        val overMaxParticipantsRoom = ChatRoom(
            name = "Over Max Room",
            createdBy = 1L,
            maxParticipants = 1001
        )
        assertFalse(overMaxParticipantsRoom.isValid())

        val zeroParticipantsRoom = ChatRoom(
            name = "Zero Room",
            createdBy = 1L,
            maxParticipants = 0
        )
        assertFalse(zeroParticipantsRoom.isValid())

        val maxDescriptionRoom = ChatRoom(
            name = "Max Desc Room",
            description = "a".repeat(500),
            createdBy = 1L
        )
        assertTrue(maxDescriptionRoom.isValid())
    }
}