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
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
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
    private val chatRoomRepository: ChatRoomRepository,
    private val transactionalOperator: TransactionalOperator
) {

    /**
     * 사용자를 채팅방에 참여시킵니다.
     */
    fun joinChatRoom(
        userId: Long,
        chatRoomId: Long,
        role: ChatRoomRole = ChatRoomRole.MEMBER,
        invitedBy: Long? = null
    ): Mono<UserChatRoom> {
        return validateUserAndChatRoom(userId, chatRoomId)
            .flatMap { (_, chatRoom) ->
                checkRoomCapacity(chatRoom)
                    .then(createUserChatRoomRelationship(userId, chatRoomId, role, invitedBy))
            }
            .`as`(transactionalOperator::transactional)
    }

    /**
     * 사용자가 채팅방을 떠납니다.
     */
    fun leaveChatRoom(userId: Long, chatRoomId: Long): Mono<Void> {
        return userChatRoomRepository.findByUserIdAndChatRoomId(userId, chatRoomId)
            .switchIfEmpty(Mono.error(UserChatRoomNotFoundException("사용자-채팅방 관계를 찾을 수 없습니다.")))
            .flatMap { relationship ->
                val leftRelationship = relationship.leave()
                userChatRoomRepository.update(leftRelationship)
            }
            .then()
            .`as`(transactionalOperator::transactional)
    }

    /**
     * 이미 확인된 관계 정보를 사용하여 사용자가 채팅방을 떠납니다.
     */
    fun leaveChatRoomWithRelationship(relationship: UserChatRoom): Mono<Void> {
        val leftRelationship = relationship.leave()
        return userChatRoomRepository.update(leftRelationship)
            .then()
            .`as`(transactionalOperator::transactional)
    }

    /**
     * 사용자가 채팅방에 다시 참여합니다. (비활성 상태에서 활성 상태로 변경)
     */
    fun rejoinChatRoom(userId: Long, chatRoomId: Long): Mono<UserChatRoom> {
        return userChatRoomRepository.findByUserIdAndChatRoomId(userId, chatRoomId)
            .switchIfEmpty(Mono.error(UserChatRoomNotFoundException("사용자-채팅방 관계를 찾을 수 없습니다.")))
            .flatMap { relationship ->
                if (relationship.isActive) {
                    Mono.just(relationship)
                } else {
                    val rejoinedRelationship = relationship.rejoin()
                    userChatRoomRepository.update(rejoinedRelationship)
                }
            }
            .`as`(transactionalOperator::transactional)
    }

    /**
     * 사용자를 채팅방에서 추방합니다.
     */
    fun kickUserFromChatRoom(
        requesterId: Long,
        targetUserId: Long,
        chatRoomId: Long
    ): Mono<Void> {
        return validateKickPermission(requesterId, targetUserId, chatRoomId)
            .then(leaveChatRoom(targetUserId, chatRoomId))
            .`as`(transactionalOperator::transactional)
    }

    /**
     * 사용자의 채팅방 역할을 변경합니다.
     */
    fun changeUserRole(
        requesterId: Long,
        targetUserId: Long,
        chatRoomId: Long,
        newRole: ChatRoomRole
    ): Mono<UserChatRoom> {
        return validateRoleChangePermission(requesterId, targetUserId, chatRoomId, newRole)
            .flatMap { targetRelationship ->
                val updatedRelationship = targetRelationship.changeRole(newRole)
                userChatRoomRepository.update(updatedRelationship)
            }
            .`as`(transactionalOperator::transactional)
    }

    /**
     * 사용자의 마지막 읽음 시간을 업데이트합니다.
     */
    fun markAsRead(
        userId: Long,
        chatRoomId: Long,
        readTime: LocalDateTime = LocalDateTime.now()
    ): Mono<UserChatRoom> {
        return userChatRoomRepository.findByUserIdAndChatRoomId(userId, chatRoomId)
            .switchIfEmpty(Mono.error(UserChatRoomNotFoundException("사용자-채팅방 관계를 찾을 수 없습니다.")))
            .flatMap { relationship ->
                val updatedRelationship = relationship.markAsRead(readTime)
                userChatRoomRepository.update(updatedRelationship)
            }
    }

    /**
     * 채팅방 음소거 상태를 토글합니다.
     */
    fun toggleMute(userId: Long, chatRoomId: Long): Mono<UserChatRoom> {
        return userChatRoomRepository.findByUserIdAndChatRoomId(userId, chatRoomId)
            .switchIfEmpty(Mono.error(UserChatRoomNotFoundException("사용자-채팅방 관계를 찾을 수 없습니다.")))
            .flatMap { relationship ->
                val updatedRelationship = relationship.toggleMute()
                userChatRoomRepository.update(updatedRelationship)
            }
    }

    /**
     * 채팅방 고정 상태를 토글합니다.
     */
    fun togglePin(userId: Long, chatRoomId: Long): Mono<UserChatRoom> {
        return userChatRoomRepository.findByUserIdAndChatRoomId(userId, chatRoomId)
            .switchIfEmpty(Mono.error(UserChatRoomNotFoundException("사용자-채팅방 관계를 찾을 수 없습니다.")))
            .flatMap { relationship ->
                val updatedRelationship = relationship.togglePin()
                userChatRoomRepository.update(updatedRelationship)
            }
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
    private fun validateUserAndChatRoom(userId: Long, chatRoomId: Long): Mono<Pair<User, ChatRoom>> {
        val userMono = userRepository.findById(userId)
            .switchIfEmpty(Mono.error(UserNotFoundException("사용자를 찾을 수 없습니다.")))
        
        val chatRoomMono = chatRoomRepository.findById(chatRoomId)
            .switchIfEmpty(Mono.error(ChatRoomNotFoundException("채팅방을 찾을 수 없습니다.")))
        
        return Mono.zip(userMono, chatRoomMono) { user, chatRoom ->
            Pair(user, chatRoom)
        }
    }

    /**
     * 채팅방 참여자 수 제한을 확인합니다.
     */
    private fun checkRoomCapacity(chatRoom: ChatRoom): Mono<Void> {
        return userChatRoomRepository.countActiveParticipantsByChatRoomId(chatRoom.id!!)
            .flatMap { currentParticipants ->
                if (chatRoom.isAtCapacity(currentParticipants.toInt())) {
                    Mono.error(RuntimeException("채팅방이 정원에 도달했습니다."))
                } else {
                    Mono.empty()
                }
            }
    }

    /**
     * 사용자-채팅방 관계를 생성합니다.
     */
    private fun createUserChatRoomRelationship(
        userId: Long,
        chatRoomId: Long,
        role: ChatRoomRole,
        invitedBy: Long?
    ): Mono<UserChatRoom> {
        val relationship = UserChatRoom(
            userId = userId,
            chatRoomId = chatRoomId,
            role = role,
            joinedAt = LocalDateTime.now(),
            isActive = true,
            invitedBy = invitedBy
        )
        
        return userChatRoomRepository.save(relationship)
    }

    /**
     * 추방 권한을 검증합니다.
     */
    private fun validateKickPermission(
        requesterId: Long,
        targetUserId: Long,
        chatRoomId: Long
    ): Mono<Void> {
        if (requesterId == targetUserId) {
            return Mono.error(InsufficientPermissionException("자기 자신을 추방할 수 없습니다."))
        }

        val requesterRelationshipMono = userChatRoomRepository.findByUserIdAndChatRoomId(requesterId, chatRoomId)
            .switchIfEmpty(Mono.error(UserChatRoomNotFoundException("요청자의 채팅방 관계를 찾을 수 없습니다.")))

        val targetRelationshipMono = userChatRoomRepository.findByUserIdAndChatRoomId(targetUserId, chatRoomId)
            .switchIfEmpty(Mono.error(UserChatRoomNotFoundException("대상 사용자의 채팅방 관계를 찾을 수 없습니다.")))

        return Mono.zip(requesterRelationshipMono, targetRelationshipMono)
            .flatMap { tuple ->
                val requesterRelationship = tuple.t1
                val targetRelationship = tuple.t2
                if (!requesterRelationship.canKickUser(targetRelationship.role)) {
                    Mono.error(InsufficientPermissionException("사용자를 추방할 권한이 없습니다."))
                } else {
                    Mono.empty()
                }
            }
    }

    /**
     * 역할 변경 권한을 검증합니다.
     */
    private fun validateRoleChangePermission(
        requesterId: Long,
        targetUserId: Long,
        chatRoomId: Long,
        newRole: ChatRoomRole
    ): Mono<UserChatRoom> {
        if (requesterId == targetUserId) {
            return Mono.error(InsufficientPermissionException("자신의 역할을 변경할 수 없습니다."))
        }

        val requesterRelationshipMono = userChatRoomRepository.findByUserIdAndChatRoomId(requesterId, chatRoomId)
            .switchIfEmpty(Mono.error(UserChatRoomNotFoundException("요청자의 채팅방 관계를 찾을 수 없습니다.")))

        val targetRelationshipMono = userChatRoomRepository.findByUserIdAndChatRoomId(targetUserId, chatRoomId)
            .switchIfEmpty(Mono.error(UserChatRoomNotFoundException("대상 사용자의 채팅방 관계를 찾을 수 없습니다.")))

        return Mono.zip(requesterRelationshipMono, targetRelationshipMono)
            .flatMap { tuple ->
                val requesterRelationship = tuple.t1
                val targetRelationship = tuple.t2
                if (!requesterRelationship.canChangeRoleOf(targetRelationship.role) ||
                    !requesterRelationship.canChangeRoleOf(newRole)) {
                    Mono.error(InsufficientPermissionException("역할을 변경할 권한이 없습니다."))
                } else {
                    Mono.just(targetRelationship)
                }
            }
    }
    
    /**
     * 채팅방의 모든 사용자-채팅방 관계를 삭제합니다. (채팅방 삭제 시 사용)
     */
    fun deleteAllByChatRoomId(chatRoomId: Long): Mono<Void> {
        return userChatRoomRepository.deleteByChatRoomId(chatRoomId)
            .`as`(transactionalOperator::transactional)
    }
    
    /**
     * 채팅방의 총 참여자 수를 조회합니다. (모든 상태 포함)
     */
    fun countTotalParticipants(chatRoomId: Long): Mono<Long> {
        return userChatRoomRepository.countByChatRoomId(chatRoomId)
    }
}