package com.simplechat.domain.entity

import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

/**
 * 사용자-채팅방 관계를 나타내는 도메인 엔티티
 * 
 * 사용자가 특정 채팅방에 참여하는 관계와 그 역할을 관리합니다.
 * 다대다 관계의 연결 테이블 역할을 하며, 추가적인 비즈니스 정보를 포함합니다.
 */
data class UserChatRoom(
    val userId: Long,
    
    val chatRoomId: Long,
    
    @field:NotNull(message = "채팅방 역할은 필수입니다.")
    val role: ChatRoomRole = ChatRoomRole.MEMBER,
    
    val joinedAt: LocalDateTime = LocalDateTime.now(),
    
    val isActive: Boolean = true, // 활성 상태 (나가기/차단 등으로 비활성화 가능)
    
    val lastReadAt: LocalDateTime? = null, // 마지막 읽은 시간 (읽음 처리용)
    
    val isMuted: Boolean = false, // 음소거 상태
    
    val isPinned: Boolean = false, // 채팅방 고정 상태
    
    val leftAt: LocalDateTime? = null, // 채팅방을 나간 시간
    
    val invitedBy: Long? = null, // 초대한 사용자 ID
    
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    
    /**
     * 사용자-채팅방 관계가 유효한지 검증합니다.
     */
    fun isValid(): Boolean {
        return userId > 0 && 
               chatRoomId > 0 &&
               (leftAt == null || leftAt.isAfter(joinedAt))
    }
    
    /**
     * 사용자가 현재 채팅방에 활성 상태로 참여 중인지 확인합니다.
     */
    fun isActiveParticipant(): Boolean {
        return isActive && leftAt == null
    }
    
    /**
     * 사용자가 채팅방을 떠났는지 확인합니다.
     */
    fun hasLeft(): Boolean {
        return leftAt != null
    }
    
    /**
     * 사용자가 채팅방에서 관리자 권한 이상인지 확인합니다.
     */
    fun hasAdminPrivileges(): Boolean {
        return role.isAdminOrAbove()
    }
    
    /**
     * 사용자가 채팅방 소유자인지 확인합니다.
     */
    fun isOwner(): Boolean {
        return role.isOwner()
    }
    
    /**
     * 다른 사용자의 역할을 변경할 수 있는 권한이 있는지 확인합니다.
     */
    fun canChangeRoleOf(targetRole: ChatRoomRole): Boolean {
        return role.hasHigherAuthorityThan(targetRole)
    }
    
    /**
     * 다른 사용자를 채팅방에서 추방할 수 있는 권한이 있는지 확인합니다.
     */
    fun canKickUser(targetRole: ChatRoomRole): Boolean {
        return role.hasHigherAuthorityThan(targetRole)
    }
    
    /**
     * 채팅방 설정을 변경할 수 있는 권한이 있는지 확인합니다.
     */
    fun canModifyRoomSettings(): Boolean {
        return role.isAdminOrAbove()
    }
    
    /**
     * 채팅방을 삭제할 수 있는 권한이 있는지 확인합니다.
     */
    fun canDeleteRoom(): Boolean {
        return role.isOwner()
    }
    
    /**
     * 사용자가 채팅방을 나가면서 관계를 비활성화합니다.
     */
    fun leave(): UserChatRoom {
        return this.copy(
            isActive = false,
            leftAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }
    
    /**
     * 사용자가 다시 채팅방에 참여합니다.
     */
    fun rejoin(): UserChatRoom {
        return this.copy(
            isActive = true,
            leftAt = null,
            joinedAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }
    
    /**
     * 사용자의 역할을 변경합니다.
     */
    fun changeRole(newRole: ChatRoomRole): UserChatRoom {
        return this.copy(
            role = newRole,
            updatedAt = LocalDateTime.now()
        )
    }
    
    /**
     * 마지막 읽음 시간을 업데이트합니다.
     */
    fun markAsRead(readTime: LocalDateTime = LocalDateTime.now()): UserChatRoom {
        return this.copy(
            lastReadAt = readTime,
            updatedAt = LocalDateTime.now()
        )
    }
    
    /**
     * 음소거 상태를 토글합니다.
     */
    fun toggleMute(): UserChatRoom {
        return this.copy(
            isMuted = !isMuted,
            updatedAt = LocalDateTime.now()
        )
    }
    
    /**
     * 채팅방 고정 상태를 토글합니다.
     */
    fun togglePin(): UserChatRoom {
        return this.copy(
            isPinned = !isPinned,
            updatedAt = LocalDateTime.now()
        )
    }
    
    /**
     * 참여 기간을 계산합니다 (일 단위).
     */
    fun getParticipationDays(): Long {
        val endTime = leftAt ?: LocalDateTime.now()
        return java.time.Duration.between(joinedAt, endTime).toDays()
    }
    
    /**
     * 읽지 않은 메시지가 있을 가능성이 있는지 확인합니다.
     * (마지막 읽음 시간이 없거나, 업데이트 시간보다 이전인 경우)
     */
    fun mayHaveUnreadMessages(): Boolean {
        return lastReadAt == null || lastReadAt.isBefore(updatedAt)
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserChatRoom) return false
        return userId == other.userId && chatRoomId == other.chatRoomId
    }

    override fun hashCode(): Int {
        var result = userId.hashCode()
        result = 31 * result + chatRoomId.hashCode()
        return result
    }

    override fun toString(): String {
        return "UserChatRoom(userId=$userId, chatRoomId=$chatRoomId, role=$role, isActive=$isActive, joinedAt=$joinedAt)"
    }
}