package com.simplechat.service

import com.simplechat.domain.entity.ChatRoom
import com.simplechat.domain.entity.ChatRoomRole
import com.simplechat.domain.entity.User
import com.simplechat.domain.entity.UserChatRoom
import com.simplechat.domain.exception.ChatRoomNotFoundException
import com.simplechat.domain.exception.InsufficientPermissionException
import com.simplechat.domain.exception.UserChatRoomNotFoundException
import com.simplechat.domain.exception.UserNotFoundException
import com.simplechat.domain.repository.ChatRoomRepository
import com.simplechat.domain.repository.UserChatRoomRepository
import com.simplechat.domain.repository.UserRepository
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDateTime

/**
 * 사용자-채팅방 관계 관리를 위한 서비스 클래스
 * 
 * 사용자와 채팅방 간의 관계 생성, 조회, 수정, 삭제 및 권한 관리를 담당합니다.
 */
@Service
class UserChatRoomService(
    private val userChatRoomRepository: UserChatRoomRepository,
    private val userRepository: UserRepository,
    private val chatRoomRepository: ChatRoomRepository
) {

    /**
     * 사용자를 채팅방에 참여시킵니다.
     */
    @Transactional
    suspend fun joinChatRoom(
        userId: Long,
        chatRoomId: Long,
        role: ChatRoomRole = ChatRoomRole.MEMBER,
        invitedBy: Long? = null
    ): UserChatRoom {
        val (_, chatRoom) = validateUserAndChatRoom(userId, chatRoomId)
        checkRoomCapacity(chatRoom)
        return createUserChatRoomRelationship(userId, chatRoomId, role, invitedBy)
    }

    /**
     * 사용자가 채팅방을 떠납니다.
     */
    @Transactional
    suspend fun leaveChatRoom(userId: Long, chatRoomId: Long) {
        val relationship = userChatRoomRepository.findByUserIdAndChatRoomId(userId, chatRoomId).awaitSingleOrNull()
            ?: throw UserChatRoomNotFoundException("사용자-채팅방 관계를 찾을 수 없습니다.")
        val leftRelationship = relationship.leave()
        userChatRoomRepository.update(leftRelationship).awaitSingle()
    }

    /**
     * 이미 확인된 관계 정보를 사용하여 사용자가 채팅방을 떠납니다.
     */
    @Transactional
    suspend fun leaveChatRoomWithRelationship(relationship: UserChatRoom) {
        val leftRelationship = relationship.leave()
        userChatRoomRepository.update(leftRelationship).awaitSingle()
    }

    /**
     * 사용자가 채팅방에 다시 참여합니다. (비활성 상태에서 활성 상태로 변경)
     */
    @Transactional
    suspend fun rejoinChatRoom(userId: Long, chatRoomId: Long): UserChatRoom {
        val relationship = userChatRoomRepository.findByUserIdAndChatRoomId(userId, chatRoomId).awaitSingleOrNull()
            ?: throw UserChatRoomNotFoundException("사용자-채팅방 관계를 찾을 수 없습니다.")
        
        return if (relationship.isActive) {
            relationship
        } else {
            val rejoinedRelationship = relationship.rejoin()
            userChatRoomRepository.update(rejoinedRelationship).awaitSingle()
        }
    }

    /**
     * 사용자를 채팅방에서 추방합니다.
     */
    @Transactional
    suspend fun kickUserFromChatRoom(
        requesterId: Long,
        targetUserId: Long,
        chatRoomId: Long
    ) {
        validateKickPermission(requesterId, targetUserId, chatRoomId)
        leaveChatRoom(targetUserId, chatRoomId)
    }

    /**
     * 사용자의 채팅방 역할을 변경합니다.
     */
    @Transactional
    suspend fun changeUserRole(
        requesterId: Long,
        targetUserId: Long,
        chatRoomId: Long,
        newRole: ChatRoomRole
    ): UserChatRoom {
        val targetRelationship = validateRoleChangePermission(requesterId, targetUserId, chatRoomId, newRole)
        val updatedRelationship = targetRelationship.changeRole(newRole)
        return userChatRoomRepository.update(updatedRelationship).awaitSingle()
    }

    /**
     * 사용자의 마지막 읽음 시간을 업데이트합니다.
     */
    suspend fun markAsRead(
        userId: Long,
        chatRoomId: Long,
        readTime: LocalDateTime = LocalDateTime.now()
    ): UserChatRoom {
        val relationship = userChatRoomRepository.findByUserIdAndChatRoomId(userId, chatRoomId).awaitSingleOrNull()
            ?: throw UserChatRoomNotFoundException("사용자-채팅방 관계를 찾을 수 없습니다.")
        
        val updatedRelationship = relationship.markAsRead(readTime)
        return userChatRoomRepository.update(updatedRelationship).awaitSingle()
    }

    /**
     * 채팅방 음소거 상태를 토글합니다.
     */
    suspend fun toggleMute(userId: Long, chatRoomId: Long): UserChatRoom {
        val relationship = userChatRoomRepository.findByUserIdAndChatRoomId(userId, chatRoomId).awaitSingleOrNull()
            ?: throw UserChatRoomNotFoundException("사용자-채팅방 관계를 찾을 수 없습니다.")

        val updatedRelationship = relationship.toggleMute()
        return userChatRoomRepository.update(updatedRelationship).awaitSingle()
    }

    /**
     * 채팅방 고정 상태를 토글합니다.
     */
    suspend fun togglePin(userId: Long, chatRoomId: Long): UserChatRoom {
        val relationship = userChatRoomRepository.findByUserIdAndChatRoomId(userId, chatRoomId).awaitSingleOrNull()
            ?: throw UserChatRoomNotFoundException("사용자-채팅방 관계를 찾을 수 없습니다.")

        val updatedRelationship = relationship.togglePin()
        return userChatRoomRepository.update(updatedRelationship).awaitSingle()
    }

    /**
     * 사용자가 참여한 모든 활성 채팅방을 조회합니다.
     */
    fun getUserActiveRooms(userId: Long): Flux<UserChatRoom> {
        return userChatRoomRepository.findActiveRoomsByUserId(userId)
    }

    /**
     * 사용자가 고정한 채팅방들을 조회합니다.
     */
    fun getUserPinnedRooms(userId: Long): Flux<UserChatRoom> {
        return userChatRoomRepository.findPinnedRoomsByUserId(userId)
    }

    /**
     * 사용자가 음소거한 채팅방들을 조회합니다.
     */
    fun getUserMutedRooms(userId: Long): Flux<UserChatRoom> {
        return userChatRoomRepository.findMutedRoomsByUserId(userId)
    }

    /**
     * 사용자의 읽지 않은 메시지가 있을 가능성이 있는 채팅방들을 조회합니다.
     */
    fun getUserUnreadRooms(userId: Long): Flux<UserChatRoom> {
        return userChatRoomRepository.findUnreadRoomsByUserId(userId)
    }

    /**
     * 특정 채팅방의 모든 활성 참여자를 조회합니다.
     */
    fun getChatRoomActiveParticipants(chatRoomId: Long): Flux<UserChatRoom> {
        return userChatRoomRepository.findActiveParticipantsByChatRoomId(chatRoomId)
    }

    /**
     * 특정 채팅방의 관리자들을 조회합니다.
     */
    fun getChatRoomAdmins(chatRoomId: Long): Flux<UserChatRoom> {
        return userChatRoomRepository.findAdminsByChatRoomId(chatRoomId)
    }

    /**
     * 특정 채팅방의 소유자를 조회합니다.
     */
    fun getChatRoomOwner(chatRoomId: Long): Mono<UserChatRoom> {
        return userChatRoomRepository.findOwnerByChatRoomId(chatRoomId)
    }

    /**
     * 특정 역할을 가진 사용자 수를 조회합니다.
     */
    fun countUsersByRole(role: ChatRoomRole): Mono<Long> {
        return userChatRoomRepository.countByRole(role)
    }

    /**
     * 채팅방의 활성 참여자 수를 조회합니다.
     */
    fun countActiveParticipants(chatRoomId: Long): Mono<Long> {
        return userChatRoomRepository.countActiveParticipantsByChatRoomId(chatRoomId)
    }

    /**
     * 사용자가 참여한 활성 채팅방 수를 조회합니다.
     */
    fun countUserActiveRooms(userId: Long): Mono<Long> {
        return userChatRoomRepository.countActiveRoomsByUserId(userId)
    }

    /**
     * 사용자가 특정 채팅방에 활성 참여 중인지 확인합니다.
     */
    fun isActiveParticipant(userId: Long, chatRoomId: Long): Mono<Boolean> {
        return userChatRoomRepository.isActiveParticipant(userId, chatRoomId)
    }

    /**
     * 사용자의 특정 채팅방에서의 관계 정보를 조회합니다.
     */
    fun getUserChatRoomRelationship(userId: Long, chatRoomId: Long): Mono<UserChatRoom> {
        return userChatRoomRepository.findByUserIdAndChatRoomId(userId, chatRoomId)
            .switchIfEmpty(Mono.error(UserChatRoomNotFoundException("사용자-채팅방 관계를 찾을 수 없습니다.")))
    }

    /**
     * 사용자의 특정 채팅방에서의 관계 정보를 조회합니다. (관계가 없어도 예외를 던지지 않음)
     */
    fun findUserChatRoomRelationship(userId: Long, chatRoomId: Long): Mono<UserChatRoom> {
        return userChatRoomRepository.findByUserIdAndChatRoomId(userId, chatRoomId)
    }

    // === Private Helper Methods ===

    /**
     * 사용자와 채팅방이 존재하는지 검증합니다.
     */
    private suspend fun validateUserAndChatRoom(userId: Long, chatRoomId: Long): Pair<User, ChatRoom> {
        val user = userRepository.findById(userId).awaitSingleOrNull()
            ?: throw UserNotFoundException("사용자를 찾을 수 없습니다.")
        
        val chatRoom = chatRoomRepository.findById(chatRoomId).awaitSingleOrNull()
            ?: throw ChatRoomNotFoundException("채팅방을 찾을 수 없습니다.")
        
        return Pair(user, chatRoom)
    }

    /**
     * 채팅방 참여자 수 제한을 확인합니다.
     */
    private suspend fun checkRoomCapacity(chatRoom: ChatRoom) {
        val currentParticipants = userChatRoomRepository.countActiveParticipantsByChatRoomId(chatRoom.id!!).awaitSingle()
        if (chatRoom.isAtCapacity(currentParticipants.toInt())) {
            throw RuntimeException("채팅방이 정원에 도달했습니다.")
        }
    }

    /**
     * 사용자-채팅방 관계를 생성합니다.
     */
    private suspend fun createUserChatRoomRelationship(
        userId: Long,
        chatRoomId: Long,
        role: ChatRoomRole,
        invitedBy: Long?
    ): UserChatRoom {
        val relationship = UserChatRoom(
            userId = userId,
            chatRoomId = chatRoomId,
            role = role,
            joinedAt = LocalDateTime.now(),
            isActive = true,
            invitedBy = invitedBy
        )
        
        return userChatRoomRepository.save(relationship).awaitSingle()
    }

    /**
     * 추방 권한을 검증합니다.
     */
    private suspend fun validateKickPermission(
        requesterId: Long,
        targetUserId: Long,
        chatRoomId: Long
    ) {
        if (requesterId == targetUserId) {
            throw InsufficientPermissionException("자기 자신을 추방할 수 없습니다.")
        }

        val requesterRelationship = userChatRoomRepository.findByUserIdAndChatRoomId(requesterId, chatRoomId).awaitSingleOrNull()
            ?: throw UserChatRoomNotFoundException("요청자의 채팅방 관계를 찾을 수 없습니다.")

        val targetRelationship = userChatRoomRepository.findByUserIdAndChatRoomId(targetUserId, chatRoomId).awaitSingleOrNull()
            ?: throw UserChatRoomNotFoundException("대상 사용자의 채팅방 관계를 찾을 수 없습니다.")

        if (!requesterRelationship.canKickUser(targetRelationship.role)) {
            throw InsufficientPermissionException("사용자를 추방할 권한이 없습니다.")
        }
    }

    /**
     * 역할 변경 권한을 검증합니다.
     */
    private suspend fun validateRoleChangePermission(
        requesterId: Long,
        targetUserId: Long,
        chatRoomId: Long,
        newRole: ChatRoomRole
    ): UserChatRoom {
        if (requesterId == targetUserId) {
            throw InsufficientPermissionException("자신의 역할을 변경할 수 없습니다.")
        }

        val requesterRelationship = userChatRoomRepository.findByUserIdAndChatRoomId(requesterId, chatRoomId).awaitSingleOrNull()
            ?: throw UserChatRoomNotFoundException("요청자의 채팅방 관계를 찾을 수 없습니다.")

        val targetRelationship = userChatRoomRepository.findByUserIdAndChatRoomId(targetUserId, chatRoomId).awaitSingleOrNull()
            ?: throw UserChatRoomNotFoundException("대상 사용자의 채팅방 관계를 찾을 수 없습니다.")

        if (!requesterRelationship.canChangeRoleOf(targetRelationship.role) ||
            !requesterRelationship.canChangeRoleOf(newRole)) {
            throw InsufficientPermissionException("역할을 변경할 권한이 없습니다.")
        }
        
        return targetRelationship
    }
    
    /**
     * 채팅방의 모든 사용자-채팅방 관계를 삭제합니다. (채팅방 삭제 시 사용)
     */
    @Transactional
    suspend fun deleteAllByChatRoomId(chatRoomId: Long) {
        userChatRoomRepository.deleteByChatRoomId(chatRoomId).awaitSingleOrNull()
    }
    
    /**
     * 채팅방의 총 참여자 수를 조회합니다. (모든 상태 포함)
     */
    fun countTotalParticipants(chatRoomId: Long): Mono<Long> {
        return userChatRoomRepository.countByChatRoomId(chatRoomId)
    }
}