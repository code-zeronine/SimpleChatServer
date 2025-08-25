package com.simplechat.service

import com.simplechat.domain.entity.ChatRoom
import com.simplechat.domain.entity.ChatRoomRole
import com.simplechat.domain.entity.User
import com.simplechat.domain.entity.UserChatRoom
import com.simplechat.exception.ChatRoomNotFoundException
import com.simplechat.exception.InsufficientPermissionException
import com.simplechat.exception.UserNotFoundException
import com.simplechat.exception.BusinessLogicException
import com.simplechat.infrastructure.repository.ChatRoomRepository
import com.simplechat.infrastructure.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDateTime

/**
 * 채팅방 관리를 위한 핵심 비즈니스 서비스 클래스
 * 
 * 채팅방의 생성, 조회, 수정, 삭제와 참여자 관리를 담당합니다.
 * R2DBC 환경에서 TransactionalOperator를 사용하여 Reactive Transaction을 처리합니다.
 */
@Service
class ChatRoomService(
    private val chatRoomRepository: ChatRoomRepository,
    private val userRepository: UserRepository,
    private val userChatRoomService: UserChatRoomService,
    private val transactionalOperator: TransactionalOperator
) {

    /**
     * 새로운 채팅방을 생성합니다.
     * 생성자는 자동으로 OWNER 역할로 참여됩니다.
     */
    fun createChatRoom(
        name: String,
        description: String? = null,
        isPrivate: Boolean = false,
        maxParticipants: Int = 100,
        ownerId: Long
    ): Mono<ChatRoom> {
        return validateUser(ownerId)
            .flatMap { user ->
                checkRoomNameAvailability(name)
                    .flatMap { createAndSaveChatRoom(ownerId, name, description, isPrivate, maxParticipants) }
            }
            .flatMap { savedRoom ->
                // 생성자를 소유자로 자동 참여
                userChatRoomService.joinChatRoom(ownerId, savedRoom.id!!, ChatRoomRole.OWNER)
                    .thenReturn(savedRoom)
            }
            .`as`(transactionalOperator::transactional)
    }

    /**
     * 채팅방 정보를 업데이트합니다.
     * ADMIN 이상 권한이 필요합니다.
     */
    fun updateChatRoom(
        id: Long,
        name: String? = null,
        description: String? = null,
        maxParticipants: Int? = null,
        requesterId: Long
    ): Mono<ChatRoom> {
        return validateRoomUpdatePermission(requesterId, id)
            .flatMap { room ->
                // 이름 변경 시 중복 검사
                if (name != null && name != room.name) {
                    checkRoomNameAvailability(name)
                        .flatMap { updateAndSaveRoom(room, name, description, maxParticipants) }
                } else {
                    updateAndSaveRoom(room, name, description, maxParticipants)
                }
            }
            .`as`(transactionalOperator::transactional)
    }

    /**
     * 채팅방을 삭제합니다. (소유자만 가능)
     */
    fun deleteChatRoom(roomId: Long, requesterId: Long): Mono<Void> {
        return validateRoomDeletionPermission(requesterId, roomId)
            .flatMap { room ->
                // 모든 참여자 관계 삭제 후 채팅방 삭제
                userChatRoomService.deleteAllByChatRoomId(roomId)
                    .then(chatRoomRepository.deleteById(roomId))
            }
            .`as`(transactionalOperator::transactional)
    }

    /**
     * 사용자가 채팅방에 참여합니다.
     */
    fun joinChatRoom(
        userId: Long,
        roomId: Long,
        invitedBy: Long? = null
    ): Mono<UserChatRoom> {
        return validateRoomJoinEligibility(userId, roomId)
            .flatMap { room ->
                val roleValidation = if (invitedBy != null) {
                    // 초대받은 경우 초대자 권한 검증
                    validateInvitePermission(invitedBy, roomId)
                        .thenReturn(ChatRoomRole.MEMBER)
                } else {
                    // 자발적 참여
                    if (room.isPrivateRoom()) {
                        Mono.error(BusinessLogicException("비공개 채팅방은 초대를 통해서만 참여할 수 있습니다."))
                    } else {
                        Mono.just(ChatRoomRole.MEMBER)
                    }
                }
                
                roleValidation.flatMap { memberRole ->
                    userChatRoomService.joinChatRoom(userId, roomId, memberRole, invitedBy)
                }
            }
            .`as`(transactionalOperator::transactional)
    }

    /**
     * 사용자가 채팅방을 떠납니다.
     */
    fun leaveChatRoom(userId: Long, roomId: Long): Mono<Void> {
        return getUserChatRoomRelationship(userId, roomId)
            .flatMap { relationship ->
                if (relationship.role == ChatRoomRole.OWNER) {
                    handleOwnerLeaving(userId, roomId)
                } else {
                    userChatRoomService.leaveChatRoom(userId, roomId)
                }
            }
            .`as`(transactionalOperator::transactional)
    }

    /**
     * 채팅방 상세 정보를 조회합니다.
     */
    fun getChatRoomDetails(roomId: Long, requesterId: Long? = null): Mono<ChatRoomDetails> {
        return chatRoomRepository.findById(roomId)
            .switchIfEmpty(Mono.error(ChatRoomNotFoundException("채팅방을 찾을 수 없습니다.")))
            .flatMap { room ->
                val participantCountMono = userChatRoomService.countActiveParticipants(roomId)
                val participantsMono = userChatRoomService.getChatRoomActiveParticipants(roomId)
                    .collectList()
                
                if (requesterId != null) {
                    userChatRoomService.getUserChatRoomRelationship(requesterId, roomId)
                        .map { relationship ->
                            ChatRoomDetails(
                                room = room,
                                participantCount = 0, // 임시값, 나중에 별도로 조회
                                participants = emptyList(), // 임시값, 나중에 별도로 조회
                                userRelationship = relationship
                            )
                        }
                        .switchIfEmpty(
                            Mono.just(ChatRoomDetails(
                                room = room,
                                participantCount = 0,
                                participants = emptyList(),
                                userRelationship = null
                            ))
                        )
                        .flatMap { details ->
                            Mono.zip(participantCountMono, participantsMono)
                                .map { tuple ->
                                    details.copy(
                                        participantCount = tuple.t1.toInt(),
                                        participants = tuple.t2
                                    )
                                }
                        }
                } else {
                    Mono.zip(participantCountMono, participantsMono)
                        .map { tuple ->
                            ChatRoomDetails(
                                room = room,
                                participantCount = tuple.t1.toInt(),
                                participants = tuple.t2,
                                userRelationship = null
                            )
                        }
                }
            }
    }

    /**
     * 특정 채팅방을 ID로 조회합니다.
     */
    fun findChatRoomById(roomId: Long): Mono<ChatRoom> {
        return chatRoomRepository.findById(roomId)
            .switchIfEmpty(Mono.error(ChatRoomNotFoundException("채팅방을 찾을 수 없습니다.")))
    }

    /**
     * 공개 채팅방 목록을 조회합니다.
     */
    fun getPublicRooms(limit: Int = 20): Flux<ChatRoom> {
        return chatRoomRepository.findPublicRooms()
            .take(limit.toLong())
    }

    /**
     * 사용자가 참여한 채팅방 목록을 조회합니다.
     */
    fun getUserRooms(userId: Long): Flux<ChatRoomWithRelationship> {
        return userChatRoomService.getUserActiveRooms(userId)
            .flatMap { relationship ->
                chatRoomRepository.findById(relationship.chatRoomId)
                    .map { room ->
                        ChatRoomWithRelationship(room, relationship)
                    }
            }
    }

    /**
     * 채팅방을 이름으로 검색합니다.
     */
    fun searchRoomsByName(query: String, limit: Int = 10): Flux<ChatRoom> {
        return chatRoomRepository.findByNameContaining(query)
            .take(limit.toLong())
    }

    /**
     * 공개 채팅방을 검색합니다.
     */
    fun searchPublicRooms(query: String, limit: Int = 10): Flux<ChatRoom> {
        return chatRoomRepository.findByNameContaining(query)
            .filter { !it.isPrivateRoom() }
            .take(limit.toLong())
    }

    /**
     * 최근 생성된 채팅방 목록을 조회합니다.
     */
    fun getRecentRooms(limit: Int = 10): Flux<ChatRoom> {
        return chatRoomRepository.findRecentRooms(limit)
    }

    /**
     * 활성 채팅방 목록을 조회합니다.
     */
    fun getActiveRooms(limit: Int = 20): Flux<ChatRoom> {
        return chatRoomRepository.findActiveRooms()
            .take(limit.toLong())
    }

    /**
     * 특정 사용자가 생성한 채팅방 목록을 조회합니다.
     */
    fun getRoomsByCreator(creatorId: Long): Flux<ChatRoom> {
        return chatRoomRepository.findByCreatedBy(creatorId)
    }

    /**
     * 채팅방 참여자 목록을 조회합니다.
     */
    fun getRoomParticipants(roomId: Long, requesterId: Long? = null): Flux<UserChatRoom> {
        return if (requesterId != null) {
            // 요청자가 해당 채팅방 참여자인지 확인
            userChatRoomService.isActiveParticipant(requesterId, roomId)
                .flatMapMany { isParticipant ->
                    if (isParticipant) {
                        userChatRoomService.getChatRoomActiveParticipants(roomId)
                    } else {
                        Flux.error(InsufficientPermissionException("참여자 목록을 조회할 권한이 없습니다."))
                    }
                }
        } else {
            userChatRoomService.getChatRoomActiveParticipants(roomId)
        }
    }

    /**
     * 채팅방 통계 정보를 조회합니다.
     */
    fun getRoomStatistics(roomId: Long): Mono<ChatRoomStatistics> {
        return chatRoomRepository.findById(roomId)
            .switchIfEmpty(Mono.error(ChatRoomNotFoundException("채팅방을 찾을 수 없습니다.")))
            .flatMap { room ->
                val activeCountMono = userChatRoomService.countActiveParticipants(roomId)
                val totalCountMono = userChatRoomService.countTotalParticipants(roomId)
                
                Mono.zip(activeCountMono, totalCountMono)
                    .map { tuple ->
                        val activeCount = tuple.t1.toInt()
                        val totalCount = tuple.t2.toInt()
                        
                        ChatRoomStatistics(
                            room = room,
                            activeParticipants = activeCount,
                            totalParticipants = totalCount,
                            leftParticipants = totalCount - activeCount,
                            utilizationRate = if (room.maxParticipants > 0) {
                                (activeCount.toDouble() / room.maxParticipants * 100).toInt()
                            } else 0
                        )
                    }
            }
    }

    /**
     * 사용자 권한을 검증합니다.
     */
    fun validateUserPermission(userId: Long, roomId: Long, requiredRole: ChatRoomRole): Mono<UserChatRoom> {
        return getUserChatRoomRelationship(userId, roomId)
            .flatMap { relationship ->
                if (relationship.role.level >= requiredRole.level) {
                    Mono.just(relationship)
                } else {
                    Mono.error(InsufficientPermissionException("필요한 권한이 없습니다."))
                }
            }
    }

    // === Private Helper Methods ===

    /**
     * 사용자 존재 여부를 검증합니다.
     */
    private fun validateUser(userId: Long): Mono<User> {
        return userRepository.findById(userId)
            .switchIfEmpty(Mono.error(UserNotFoundException("사용자를 찾을 수 없습니다.")))
    }

    /**
     * 채팅방 이름 중복을 검사합니다.
     */
    private fun checkRoomNameAvailability(name: String): Mono<Boolean> {
        return chatRoomRepository.existsByName(name)
            .flatMap { exists ->
                if (exists) {
                    Mono.error(BusinessLogicException("이미 존재하는 채팅방 이름입니다."))
                } else {
                    Mono.just(true) // 이름 사용 가능
                }
            }
    }

    /**
     * 채팅방을 생성하고 저장합니다.
     */
    private fun createAndSaveChatRoom(
        creatorId: Long,
        name: String,
        description: String?,
        isPrivate: Boolean,
        maxParticipants: Int
    ): Mono<ChatRoom> {
        val newRoom = ChatRoom(
            name = name,
            description = description,
            createdBy = creatorId,
            isPrivate = isPrivate,
            maxParticipants = maxParticipants,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        return chatRoomRepository.save(newRoom)
    }

    /**
     * 채팅방 업데이트 권한을 검증하고 채팅방을 반환합니다.
     */
    private fun validateRoomUpdatePermission(requesterId: Long, roomId: Long): Mono<ChatRoom> {
        return Mono.zip(
            getUserChatRoomRelationship(requesterId, roomId),
            chatRoomRepository.findById(roomId)
                .switchIfEmpty(Mono.error(ChatRoomNotFoundException("채팅방을 찾을 수 없습니다.")))
        ).flatMap { tuple ->
            val relationship = tuple.t1
            val room = tuple.t2
            
            if (relationship.canModifyRoomSettings()) {
                Mono.just(room)
            } else {
                Mono.error(InsufficientPermissionException("채팅방 설정을 변경할 권한이 없습니다."))
            }
        }
    }

    /**
     * 채팅방을 업데이트하고 저장합니다.
     */
    private fun updateAndSaveRoom(
        room: ChatRoom,
        newName: String?,
        newDescription: String?,
        newMaxParticipants: Int?
    ): Mono<ChatRoom> {
        val updatedRoom = room.updateInfo(newName, newDescription, newMaxParticipants)
        return chatRoomRepository.save(updatedRoom)
    }

    /**
     * 채팅방 삭제 권한을 검증하고 채팅방을 반환합니다.
     */
    private fun validateRoomDeletionPermission(requesterId: Long, roomId: Long): Mono<ChatRoom> {
        return Mono.zip(
            getUserChatRoomRelationship(requesterId, roomId),
            chatRoomRepository.findById(roomId)
                .switchIfEmpty(Mono.error(ChatRoomNotFoundException("채팅방을 찾을 수 없습니다.")))
        ).flatMap { tuple ->
            val relationship = tuple.t1
            val room = tuple.t2
            
            if (relationship.canDeleteRoom()) {
                Mono.just(room)
            } else {
                Mono.error(InsufficientPermissionException("채팅방을 삭제할 권한이 없습니다."))
            }
        }
    }

    /**
     * 채팅방 참여 자격을 검증합니다.
     */
    private fun validateRoomJoinEligibility(userId: Long, roomId: Long): Mono<ChatRoom> {
        return Mono.zip(
            validateUser(userId),
            chatRoomRepository.findById(roomId)
                .switchIfEmpty(Mono.error(ChatRoomNotFoundException("채팅방을 찾을 수 없습니다."))),
            userChatRoomService.isActiveParticipant(userId, roomId),
            userChatRoomService.countActiveParticipants(roomId)
        ).flatMap { tuple ->
            val user = tuple.t1
            val room = tuple.t2
            val isAlreadyParticipant = tuple.t3
            val participantCount = tuple.t4

            if (isAlreadyParticipant) {
                Mono.error(BusinessLogicException("이미 참여중인 채팅방입니다."))
            } else if (room.maxParticipants > 0 && participantCount >= room.maxParticipants) {
                Mono.error(BusinessLogicException("채팅방이 가득 찼습니다."))
            }
            else {
                Mono.just(room)
            }
        }
    }

    /**
     * 초대 권한을 검증합니다.
     */
    private fun validateInvitePermission(inviterId: Long, roomId: Long): Mono<Void> {
        return getUserChatRoomRelationship(inviterId, roomId)
            .flatMap { relationship ->
                if (relationship.hasAdminPrivileges()) {
                    Mono.empty()
                } else {
                    Mono.error(InsufficientPermissionException("사용자를 초대할 권한이 없습니다."))
                }
            }
    }

    /**
     * 소유자가 채팅방을 떠나는 경우를 처리합니다.
     * 순환 참조를 방지하기 위해 직접 삭제 로직을 구현합니다.
     */
    private fun handleOwnerLeaving(ownerId: Long, roomId: Long): Mono<Void> {
        return userChatRoomService.getChatRoomAdmins(roomId)
            .filter { it.userId != ownerId && it.role == ChatRoomRole.ADMIN }
            .next()
            .flatMap { nextAdmin ->
                // 다음 관리자를 소유자로 승격
                userChatRoomService.changeUserRole(ownerId, nextAdmin.userId, roomId, ChatRoomRole.OWNER)
                    .then(userChatRoomService.leaveChatRoom(ownerId, roomId))
            }
            .switchIfEmpty(
                // 관리자가 없으면 일반 멤버 중 한 명을 소유자로 승격
                userChatRoomService.getChatRoomActiveParticipants(roomId)
                    .filter { it.userId != ownerId }
                    .next()
                    .flatMap { nextMember ->
                        userChatRoomService.changeUserRole(ownerId, nextMember.userId, roomId, ChatRoomRole.OWNER)
                            .then(userChatRoomService.leaveChatRoom(ownerId, roomId))
                    }
                    .switchIfEmpty(
                        // 혼자 있는 경우 직접 삭제 (순환 참조 방지)
                        userChatRoomService.leaveChatRoom(ownerId, roomId)
                            .then(userChatRoomService.deleteAllByChatRoomId(roomId))
                            .then(chatRoomRepository.deleteById(roomId))
                    )
            )
    }

    /**
     * 사용자-채팅방 관계를 조회합니다.
     */
    private fun getUserChatRoomRelationship(userId: Long, roomId: Long): Mono<UserChatRoom> {
        return userChatRoomService.getUserChatRoomRelationship(userId, roomId)
    }

    // === Data Classes ===

    /**
     * 채팅방 상세 정보를 담는 데이터 클래스
     */
    data class ChatRoomDetails(
        val room: ChatRoom,
        val participantCount: Int,
        val participants: List<UserChatRoom>,
        val userRelationship: UserChatRoom?
    )

    /**
     * 사용자 관계와 함께 반환되는 채팅방 정보
     */
    data class ChatRoomWithRelationship(
        val room: ChatRoom,
        val relationship: UserChatRoom
    )

    /**
     * 채팅방 통계 정보를 담는 데이터 클래스
     */
    data class ChatRoomStatistics(
        val room: ChatRoom,
        val activeParticipants: Int,
        val totalParticipants: Int,
        val leftParticipants: Int,
        val utilizationRate: Int // 0-100 percentage
    )
}