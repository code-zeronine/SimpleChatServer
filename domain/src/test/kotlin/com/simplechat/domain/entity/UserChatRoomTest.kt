package com.simplechat.domain.entity

import org.junit.jupiter.api.Test
import jakarta.validation.Validation
import jakarta.validation.Validator
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.time.LocalDateTime

class UserChatRoomTest {

    private val validator: Validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `should create valid user chat room relationship with required fields`() {
        // Given
        val userChatRoom = UserChatRoom(
            userId = 1L,
            chatRoomId = 100L,
            role = ChatRoomRole.MEMBER
        )

        // When
        val violations = validator.validate(userChatRoom)

        // Then
        assertTrue(violations.isEmpty(), "Valid user-chat room relationship should have no validation violations")
        assertEquals(1L, userChatRoom.userId)
        assertEquals(100L, userChatRoom.chatRoomId)
        assertEquals(ChatRoomRole.MEMBER, userChatRoom.role)
        assertTrue(userChatRoom.isActive)
        assertNull(userChatRoom.leftAt)
        assertFalse(userChatRoom.isMuted)
        assertFalse(userChatRoom.isPinned)
    }

    @Test
    fun `should create user chat room with all fields`() {
        // Given
        val joinedAt = LocalDateTime.now().minusHours(1)
        val lastReadAt = LocalDateTime.now().minusMinutes(30)
        val invitedBy = 2L
        
        val userChatRoom = UserChatRoom(
            userId = 1L,
            chatRoomId = 100L,
            role = ChatRoomRole.ADMIN,
            joinedAt = joinedAt,
            isActive = true,
            lastReadAt = lastReadAt,
            isMuted = true,
            isPinned = true,
            leftAt = null,
            invitedBy = invitedBy
        )

        // When & Then
        assertEquals(1L, userChatRoom.userId)
        assertEquals(100L, userChatRoom.chatRoomId)
        assertEquals(ChatRoomRole.ADMIN, userChatRoom.role)
        assertEquals(joinedAt, userChatRoom.joinedAt)
        assertTrue(userChatRoom.isActive)
        assertEquals(lastReadAt, userChatRoom.lastReadAt)
        assertTrue(userChatRoom.isMuted)
        assertTrue(userChatRoom.isPinned)
        assertNull(userChatRoom.leftAt)
        assertEquals(invitedBy, userChatRoom.invitedBy)
    }

    @Test
    fun `should validate user chat room relationship`() {
        // Given
        val validRelationship = UserChatRoom(
            userId = 1L,
            chatRoomId = 100L,
            role = ChatRoomRole.MEMBER
        )

        val invalidUserIdRelationship = UserChatRoom(
            userId = 0L,
            chatRoomId = 100L,
            role = ChatRoomRole.MEMBER
        )

        val invalidChatRoomIdRelationship = UserChatRoom(
            userId = 1L,
            chatRoomId = -1L,
            role = ChatRoomRole.MEMBER
        )

        val invalidLeftTimeRelationship = UserChatRoom(
            userId = 1L,
            chatRoomId = 100L,
            role = ChatRoomRole.MEMBER,
            joinedAt = LocalDateTime.now(),
            leftAt = LocalDateTime.now().minusHours(1) // Left before joined
        )

        // When & Then
        assertTrue(validRelationship.isValid())
        assertFalse(invalidUserIdRelationship.isValid())
        assertFalse(invalidChatRoomIdRelationship.isValid())
        assertFalse(invalidLeftTimeRelationship.isValid())
    }

    @Test
    fun `should check active participant status correctly`() {
        // Given
        val activeParticipant = UserChatRoom(
            userId = 1L,
            chatRoomId = 100L,
            isActive = true,
            leftAt = null
        )

        val inactiveParticipant = UserChatRoom(
            userId = 1L,
            chatRoomId = 100L,
            isActive = false,
            leftAt = null
        )

        val leftParticipant = UserChatRoom(
            userId = 1L,
            chatRoomId = 100L,
            isActive = true,
            leftAt = LocalDateTime.now()
        )

        // When & Then
        assertTrue(activeParticipant.isActiveParticipant())
        assertFalse(inactiveParticipant.isActiveParticipant())
        assertFalse(leftParticipant.isActiveParticipant())
    }

    @Test
    fun `should check if user has left correctly`() {
        // Given
        val activeParticipant = UserChatRoom(
            userId = 1L,
            chatRoomId = 100L,
            leftAt = null
        )

        val leftParticipant = UserChatRoom(
            userId = 1L,
            chatRoomId = 100L,
            leftAt = LocalDateTime.now()
        )

        // When & Then
        assertFalse(activeParticipant.hasLeft())
        assertTrue(leftParticipant.hasLeft())
    }

    @Test
    fun `should check admin privileges correctly`() {
        // Given
        val member = UserChatRoom(userId = 1L, chatRoomId = 100L, role = ChatRoomRole.MEMBER)
        val admin = UserChatRoom(userId = 1L, chatRoomId = 100L, role = ChatRoomRole.ADMIN)
        val owner = UserChatRoom(userId = 1L, chatRoomId = 100L, role = ChatRoomRole.OWNER)

        // When & Then
        assertFalse(member.hasAdminPrivileges())
        assertTrue(admin.hasAdminPrivileges())
        assertTrue(owner.hasAdminPrivileges())
    }

    @Test
    fun `should check owner status correctly`() {
        // Given
        val member = UserChatRoom(userId = 1L, chatRoomId = 100L, role = ChatRoomRole.MEMBER)
        val admin = UserChatRoom(userId = 1L, chatRoomId = 100L, role = ChatRoomRole.ADMIN)
        val owner = UserChatRoom(userId = 1L, chatRoomId = 100L, role = ChatRoomRole.OWNER)

        // When & Then
        assertFalse(member.isOwner())
        assertFalse(admin.isOwner())
        assertTrue(owner.isOwner())
    }

    @Test
    fun `should check permission to change roles correctly`() {
        // Given
        val member = UserChatRoom(userId = 1L, chatRoomId = 100L, role = ChatRoomRole.MEMBER)
        val admin = UserChatRoom(userId = 1L, chatRoomId = 100L, role = ChatRoomRole.ADMIN)
        val owner = UserChatRoom(userId = 1L, chatRoomId = 100L, role = ChatRoomRole.OWNER)

        // When & Then
        // Members can't change any roles
        assertFalse(member.canChangeRoleOf(ChatRoomRole.MEMBER))
        assertFalse(member.canChangeRoleOf(ChatRoomRole.ADMIN))
        assertFalse(member.canChangeRoleOf(ChatRoomRole.OWNER))

        // Admins can change member roles but not other admin or owner roles
        assertTrue(admin.canChangeRoleOf(ChatRoomRole.MEMBER))
        assertFalse(admin.canChangeRoleOf(ChatRoomRole.ADMIN))
        assertFalse(admin.canChangeRoleOf(ChatRoomRole.OWNER))

        // Owners can change all lower roles
        assertTrue(owner.canChangeRoleOf(ChatRoomRole.MEMBER))
        assertTrue(owner.canChangeRoleOf(ChatRoomRole.ADMIN))
        assertFalse(owner.canChangeRoleOf(ChatRoomRole.OWNER)) // Can't change other owner roles
    }

    @Test
    fun `should check permission to kick users correctly`() {
        // Given
        val member = UserChatRoom(userId = 1L, chatRoomId = 100L, role = ChatRoomRole.MEMBER)
        val admin = UserChatRoom(userId = 1L, chatRoomId = 100L, role = ChatRoomRole.ADMIN)
        val owner = UserChatRoom(userId = 1L, chatRoomId = 100L, role = ChatRoomRole.OWNER)

        // When & Then
        // Members can't kick anyone
        assertFalse(member.canKickUser(ChatRoomRole.MEMBER))
        assertFalse(member.canKickUser(ChatRoomRole.ADMIN))
        assertFalse(member.canKickUser(ChatRoomRole.OWNER))

        // Admins can kick members but not other admins or owners
        assertTrue(admin.canKickUser(ChatRoomRole.MEMBER))
        assertFalse(admin.canKickUser(ChatRoomRole.ADMIN))
        assertFalse(admin.canKickUser(ChatRoomRole.OWNER))

        // Owners can kick members and admins but not other owners
        assertTrue(owner.canKickUser(ChatRoomRole.MEMBER))
        assertTrue(owner.canKickUser(ChatRoomRole.ADMIN))
        assertFalse(owner.canKickUser(ChatRoomRole.OWNER))
    }

    @Test
    fun `should check room modification permissions correctly`() {
        // Given
        val member = UserChatRoom(userId = 1L, chatRoomId = 100L, role = ChatRoomRole.MEMBER)
        val admin = UserChatRoom(userId = 1L, chatRoomId = 100L, role = ChatRoomRole.ADMIN)
        val owner = UserChatRoom(userId = 1L, chatRoomId = 100L, role = ChatRoomRole.OWNER)

        // When & Then - canModifyRoomSettings
        assertFalse(member.canModifyRoomSettings())
        assertTrue(admin.canModifyRoomSettings())
        assertTrue(owner.canModifyRoomSettings())

        // When & Then - canDeleteRoom
        assertFalse(member.canDeleteRoom())
        assertFalse(admin.canDeleteRoom())
        assertTrue(owner.canDeleteRoom())
    }

    @Test
    fun `should leave chat room correctly`() {
        // Given
        val activeParticipant = UserChatRoom(
            userId = 1L,
            chatRoomId = 100L,
            isActive = true,
            leftAt = null
        )

        // When
        val before = LocalDateTime.now()
        val leftParticipant = activeParticipant.leave()
        val after = LocalDateTime.now()

        // Then
        assertFalse(leftParticipant.isActive)
        assertTrue(leftParticipant.leftAt!!.isAfter(before) || leftParticipant.leftAt!!.isEqual(before))
        assertTrue(leftParticipant.leftAt!!.isBefore(after) || leftParticipant.leftAt!!.isEqual(after))
        assertTrue(leftParticipant.updatedAt.isAfter(before) || leftParticipant.updatedAt.isEqual(before))
        
        // Original should be unchanged
        assertTrue(activeParticipant.isActive)
        assertNull(activeParticipant.leftAt)
    }

    @Test
    fun `should rejoin chat room correctly`() {
        // Given
        val leftParticipant = UserChatRoom(
            userId = 1L,
            chatRoomId = 100L,
            isActive = false,
            leftAt = LocalDateTime.now().minusHours(1),
            joinedAt = LocalDateTime.now().minusHours(2)
        )

        // When
        val before = LocalDateTime.now()
        val rejoinedParticipant = leftParticipant.rejoin()
        val after = LocalDateTime.now()

        // Then
        assertTrue(rejoinedParticipant.isActive)
        assertNull(rejoinedParticipant.leftAt)
        assertTrue(rejoinedParticipant.joinedAt.isAfter(before) || rejoinedParticipant.joinedAt.isEqual(before))
        assertTrue(rejoinedParticipant.joinedAt.isBefore(after) || rejoinedParticipant.joinedAt.isEqual(after))
        assertTrue(rejoinedParticipant.updatedAt.isAfter(before) || rejoinedParticipant.updatedAt.isEqual(before))
    }

    @Test
    fun `should change role correctly`() {
        // Given
        val member = UserChatRoom(
            userId = 1L,
            chatRoomId = 100L,
            role = ChatRoomRole.MEMBER
        )

        // When
        val before = LocalDateTime.now()
        val admin = member.changeRole(ChatRoomRole.ADMIN)
        val after = LocalDateTime.now()

        // Then
        assertEquals(ChatRoomRole.ADMIN, admin.role)
        assertTrue(admin.updatedAt.isAfter(before) || admin.updatedAt.isEqual(before))
        assertTrue(admin.updatedAt.isBefore(after) || admin.updatedAt.isEqual(after))
        
        // Original should be unchanged
        assertEquals(ChatRoomRole.MEMBER, member.role)
    }

    @Test
    fun `should mark as read correctly`() {
        // Given
        val participant = UserChatRoom(
            userId = 1L,
            chatRoomId = 100L,
            lastReadAt = null
        )

        val customReadTime = LocalDateTime.now().minusMinutes(5)

        // When
        val before = LocalDateTime.now()
        val markedAsRead = participant.markAsRead()
        val markedAsReadCustomTime = participant.markAsRead(customReadTime)
        val after = LocalDateTime.now()

        // Then - default read time
        assertTrue(markedAsRead.lastReadAt!!.isAfter(before) || markedAsRead.lastReadAt!!.isEqual(before))
        assertTrue(markedAsRead.lastReadAt!!.isBefore(after) || markedAsRead.lastReadAt!!.isEqual(after))
        
        // Then - custom read time
        assertEquals(customReadTime, markedAsReadCustomTime.lastReadAt)
        
        // Original should be unchanged
        assertNull(participant.lastReadAt)
    }

    @Test
    fun `should toggle mute correctly`() {
        // Given
        val participant = UserChatRoom(
            userId = 1L,
            chatRoomId = 100L,
            isMuted = false
        )

        // When
        val muted = participant.toggleMute()
        val unmuted = muted.toggleMute()

        // Then
        assertTrue(muted.isMuted)
        assertFalse(unmuted.isMuted)
        
        // Original should be unchanged
        assertFalse(participant.isMuted)
    }

    @Test
    fun `should toggle pin correctly`() {
        // Given
        val participant = UserChatRoom(
            userId = 1L,
            chatRoomId = 100L,
            isPinned = false
        )

        // When
        val pinned = participant.togglePin()
        val unpinned = pinned.togglePin()

        // Then
        assertTrue(pinned.isPinned)
        assertFalse(unpinned.isPinned)
        
        // Original should be unchanged
        assertFalse(participant.isPinned)
    }

    @Test
    fun `should calculate participation days correctly`() {
        // Given
        val joinedTime = LocalDateTime.now().minusDays(5).minusHours(3)
        
        val activeParticipant = UserChatRoom(
            userId = 1L,
            chatRoomId = 100L,
            joinedAt = joinedTime
        )

        val leftParticipant = UserChatRoom(
            userId = 1L,
            chatRoomId = 100L,
            joinedAt = joinedTime,
            leftAt = joinedTime.plusDays(3)
        )

        // When & Then
        assertTrue(activeParticipant.getParticipationDays() >= 5) // At least 5 days
        assertEquals(3, leftParticipant.getParticipationDays()) // Exactly 3 days
    }

    @Test
    fun `should check unread messages correctly`() {
        // Given
        val noReadTime = UserChatRoom(
            userId = 1L,
            chatRoomId = 100L,
            lastReadAt = null,
            updatedAt = LocalDateTime.now()
        )

        val upToDateRead = UserChatRoom(
            userId = 1L,
            chatRoomId = 100L,
            lastReadAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now().minusMinutes(5)
        )

        val outdatedRead = UserChatRoom(
            userId = 1L,
            chatRoomId = 100L,
            lastReadAt = LocalDateTime.now().minusHours(1),
            updatedAt = LocalDateTime.now().minusMinutes(5)
        )

        // When & Then
        assertTrue(noReadTime.mayHaveUnreadMessages())
        assertFalse(upToDateRead.mayHaveUnreadMessages())
        assertTrue(outdatedRead.mayHaveUnreadMessages())
    }

    @Test
    fun `should implement equals correctly based on userId and chatRoomId`() {
        // Given
        val relationship1 = UserChatRoom(
            userId = 1L,
            chatRoomId = 100L,
            role = ChatRoomRole.MEMBER
        )
        val relationship2 = UserChatRoom(
            userId = 1L,
            chatRoomId = 100L,
            role = ChatRoomRole.ADMIN // Different role, same user and room
        )
        val relationship3 = UserChatRoom(
            userId = 2L,
            chatRoomId = 100L,
            role = ChatRoomRole.MEMBER // Different user
        )
        val relationship4 = UserChatRoom(
            userId = 1L,
            chatRoomId = 200L,
            role = ChatRoomRole.MEMBER // Different room
        )

        // Then
        assertEquals(relationship1, relationship2) // Same user and room
        assertNotEquals(relationship1, relationship3) // Different user
        assertNotEquals(relationship1, relationship4) // Different room
    }

    @Test
    fun `should implement hashCode correctly based on userId and chatRoomId`() {
        // Given
        val relationship1 = UserChatRoom(
            userId = 1L,
            chatRoomId = 100L,
            role = ChatRoomRole.MEMBER
        )
        val relationship2 = UserChatRoom(
            userId = 1L,
            chatRoomId = 100L,
            role = ChatRoomRole.ADMIN // Different role, same user and room
        )

        // Then
        assertEquals(relationship1.hashCode(), relationship2.hashCode()) // Same user and room should have same hashCode
    }

    @Test
    fun `should not expose sensitive information in toString`() {
        // Given
        val relationship = UserChatRoom(
            userId = 1L,
            chatRoomId = 100L,
            role = ChatRoomRole.ADMIN,
            lastReadAt = LocalDateTime.now(),
            invitedBy = 2L
        )

        // When
        val toString = relationship.toString()

        // Then
        assertTrue(toString.contains("1"))
        assertTrue(toString.contains("100"))
        assertTrue(toString.contains("ADMIN"))
        assertFalse(toString.contains("lastReadAt"), "toString should not expose lastReadAt")
        assertFalse(toString.contains("invitedBy"), "toString should not expose invitedBy")
    }

    @Test
    fun `should create relationship with default timestamps`() {
        // Given & When
        val before = LocalDateTime.now()
        val relationship = UserChatRoom(
            userId = 1L,
            chatRoomId = 100L
        )
        val after = LocalDateTime.now()

        // Then
        assertTrue(relationship.joinedAt.isAfter(before) || relationship.joinedAt.isEqual(before))
        assertTrue(relationship.joinedAt.isBefore(after) || relationship.joinedAt.isEqual(after))
        assertTrue(relationship.updatedAt.isAfter(before) || relationship.updatedAt.isEqual(before))
        assertTrue(relationship.updatedAt.isBefore(after) || relationship.updatedAt.isEqual(after))
    }
}