package com.simplechat.domain.entity

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatRoomRoleTest {

    @Test
    fun `should have correct role hierarchy levels`() {
        // Given & When & Then
        assertEquals(1, ChatRoomRole.MEMBER.level)
        assertEquals(2, ChatRoomRole.ADMIN.level)
        assertEquals(3, ChatRoomRole.OWNER.level)
    }

    @Test
    fun `should have correct display names and descriptions`() {
        // Given & When & Then
        assertEquals("멤버", ChatRoomRole.MEMBER.displayName)
        assertEquals("관리자", ChatRoomRole.ADMIN.displayName)
        assertEquals("소유자", ChatRoomRole.OWNER.displayName)
        
        assertTrue(ChatRoomRole.MEMBER.description.contains("일반 채팅"))
        assertTrue(ChatRoomRole.ADMIN.description.contains("관리 권한"))
        assertTrue(ChatRoomRole.OWNER.description.contains("모든 권한"))
    }

    @Test
    fun `should check authority correctly`() {
        // Given
        val member = ChatRoomRole.MEMBER
        val admin = ChatRoomRole.ADMIN
        val owner = ChatRoomRole.OWNER

        // When & Then - hasHigherAuthorityThan
        assertTrue(admin.hasHigherAuthorityThan(member))
        assertTrue(owner.hasHigherAuthorityThan(admin))
        assertTrue(owner.hasHigherAuthorityThan(member))
        
        assertFalse(member.hasHigherAuthorityThan(admin))
        assertFalse(member.hasHigherAuthorityThan(owner))
        assertFalse(admin.hasHigherAuthorityThan(owner))
        
        // Same level should return false
        assertFalse(member.hasHigherAuthorityThan(member))
        assertFalse(admin.hasHigherAuthorityThan(admin))
        assertFalse(owner.hasHigherAuthorityThan(owner))
    }

    @Test
    fun `should check equal or higher authority correctly`() {
        // Given
        val member = ChatRoomRole.MEMBER
        val admin = ChatRoomRole.ADMIN
        val owner = ChatRoomRole.OWNER

        // When & Then
        assertTrue(admin.hasAuthorityEqualOrHigherThan(member))
        assertTrue(owner.hasAuthorityEqualOrHigherThan(admin))
        assertTrue(owner.hasAuthorityEqualOrHigherThan(member))
        
        // Same level should return true
        assertTrue(member.hasAuthorityEqualOrHigherThan(member))
        assertTrue(admin.hasAuthorityEqualOrHigherThan(admin))
        assertTrue(owner.hasAuthorityEqualOrHigherThan(owner))
        
        assertFalse(member.hasAuthorityEqualOrHigherThan(admin))
        assertFalse(member.hasAuthorityEqualOrHigherThan(owner))
        assertFalse(admin.hasAuthorityEqualOrHigherThan(owner))
    }

    @Test
    fun `should check admin privileges correctly`() {
        // When & Then
        assertFalse(ChatRoomRole.MEMBER.isAdminOrAbove())
        assertTrue(ChatRoomRole.ADMIN.isAdminOrAbove())
        assertTrue(ChatRoomRole.OWNER.isAdminOrAbove())
    }

    @Test
    fun `should identify owner role correctly`() {
        // When & Then
        assertFalse(ChatRoomRole.MEMBER.isOwner())
        assertFalse(ChatRoomRole.ADMIN.isOwner())
        assertTrue(ChatRoomRole.OWNER.isOwner())
    }

    @Test
    fun `should identify member role correctly`() {
        // When & Then
        assertTrue(ChatRoomRole.MEMBER.isMember())
        assertFalse(ChatRoomRole.ADMIN.isMember())
        assertFalse(ChatRoomRole.OWNER.isMember())
    }

    @Test
    fun `should find role from string correctly`() {
        // When & Then
        assertEquals(ChatRoomRole.MEMBER, ChatRoomRole.fromString("MEMBER"))
        assertEquals(ChatRoomRole.ADMIN, ChatRoomRole.fromString("ADMIN"))
        assertEquals(ChatRoomRole.OWNER, ChatRoomRole.fromString("OWNER"))
        
        // Case insensitive
        assertEquals(ChatRoomRole.MEMBER, ChatRoomRole.fromString("member"))
        assertEquals(ChatRoomRole.ADMIN, ChatRoomRole.fromString("Admin"))
        assertEquals(ChatRoomRole.OWNER, ChatRoomRole.fromString("owner"))
        
        // Invalid values
        assertNull(ChatRoomRole.fromString("INVALID"))
        assertNull(ChatRoomRole.fromString(null))
        assertNull(ChatRoomRole.fromString(""))
    }

    @Test
    fun `should find role from level correctly`() {
        // When & Then
        assertEquals(ChatRoomRole.MEMBER, ChatRoomRole.fromLevel(1))
        assertEquals(ChatRoomRole.ADMIN, ChatRoomRole.fromLevel(2))
        assertEquals(ChatRoomRole.OWNER, ChatRoomRole.fromLevel(3))
        
        // Invalid levels
        assertNull(ChatRoomRole.fromLevel(0))
        assertNull(ChatRoomRole.fromLevel(4))
        assertNull(ChatRoomRole.fromLevel(-1))
    }

    @Test
    fun `should return correct default role`() {
        // When & Then
        assertEquals(ChatRoomRole.MEMBER, ChatRoomRole.defaultRole())
    }

    @Test
    fun `should return roles sorted by level`() {
        // When
        val sortedRoles = ChatRoomRole.getAllRolesByLevel()
        
        // Then
        assertEquals(3, sortedRoles.size)
        assertEquals(ChatRoomRole.MEMBER, sortedRoles[0])
        assertEquals(ChatRoomRole.ADMIN, sortedRoles[1])
        assertEquals(ChatRoomRole.OWNER, sortedRoles[2])
        
        // Verify ordering
        for (i in 0 until sortedRoles.size - 1) {
            assertTrue(sortedRoles[i].level < sortedRoles[i + 1].level)
        }
    }

    @Test
    fun `should have all required enum values`() {
        // When
        val values = ChatRoomRole.values()
        
        // Then
        assertEquals(3, values.size)
        assertTrue(values.contains(ChatRoomRole.MEMBER))
        assertTrue(values.contains(ChatRoomRole.ADMIN))
        assertTrue(values.contains(ChatRoomRole.OWNER))
    }

    @Test
    fun `should maintain consistent enum properties`() {
        // Given
        val allRoles = ChatRoomRole.values()
        
        // When & Then - All roles should have unique levels
        val levels = allRoles.map { it.level }.toSet()
        assertEquals(allRoles.size, levels.size)
        
        // All roles should have non-empty display names
        allRoles.forEach { role ->
            assertNotNull(role.displayName)
            assertTrue(role.displayName.isNotBlank())
            assertNotNull(role.description)
            assertTrue(role.description.isNotBlank())
            assertTrue(role.level > 0)
        }
    }

    @Test
    fun `should have correct string representation`() {
        // When & Then
        assertEquals("MEMBER", ChatRoomRole.MEMBER.name)
        assertEquals("ADMIN", ChatRoomRole.ADMIN.name)
        assertEquals("OWNER", ChatRoomRole.OWNER.name)
        
        assertEquals("MEMBER", ChatRoomRole.MEMBER.toString())
        assertEquals("ADMIN", ChatRoomRole.ADMIN.toString())
        assertEquals("OWNER", ChatRoomRole.OWNER.toString())
    }

    @Test
    fun `should validate role hierarchy consistency`() {
        // Given
        val roles = ChatRoomRole.values().sortedBy { it.level }
        
        // When & Then - Each role should have higher authority than all previous roles
        for (i in roles.indices) {
            for (j in 0 until i) {
                assertTrue(
                    roles[i].hasHigherAuthorityThan(roles[j]),
                    "${roles[i]} should have higher authority than ${roles[j]}"
                )
                assertTrue(
                    roles[i].hasAuthorityEqualOrHigherThan(roles[j]),
                    "${roles[i]} should have equal or higher authority than ${roles[j]}"
                )
            }
        }
    }
}