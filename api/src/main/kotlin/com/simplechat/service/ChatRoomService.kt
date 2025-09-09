package com.simplechat.service

import com.simplechat.domain.entity.ChatRoom
import com.simplechat.domain.entity.ChatRoomRole
import com.simplechat.domain.entity.User
import com.simplechat.domain.entity.UserChatRoom
import com.simplechat.domain.exception.BusinessLogicException
import com.simplechat.domain.exception.ChatRoomNotFoundException
import com.simplechat.domain.exception.InsufficientPermissionException
import com.simplechat.domain.exception.UserNotFoundException
import com.simplechat.domain.repository.ChatRoomRepository
import com.simplechat.domain.repository.UserRepository
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
    private val userChatRoomService: UserChatRoomService
) {
    
    private val logger = LoggerFactory.getLogger(ChatRoomService::class.java)

    /**
     * 새로운 채팅방을 생성합니다.
     * 생성자는 자동으로 OWNER 역할로 참여됩니다.
     */
    @Transactional
    suspend fun createChatRoom(
        name: String,
        description: String? = null,
        isPrivate: Boolean = false,
        maxParticipants: Int = 100,
        ownerId: Long
    ): ChatRoom {
        validateUser(ownerId)
        checkRoomNameAvailability(name)
        val savedRoom = createAndSaveChatRoom(ownerId, name, description, isPrivate, maxParticipants)
        
        // 생성자를 소유자로 자동 참여
        userChatRoomService.joinChatRoom(ownerId, savedRoom.id!!, ChatRoomRole.OWNER)
        
        return savedRoom
    }

    /**
     * 채팅방 정보를 업데이트합니다.
     * ADMIN 이상 권한이 필요합니다.
     */
    @Transactional
    suspend fun updateChatRoom(
        id: Long,
        name: String? = null,
        description: String? = null,
        maxParticipants: Int? = null,
        requesterId: Long
    ): ChatRoom {
        val room = validateRoomUpdatePermission(requesterId, id)
        
        // 이름 변경 시 중복 검사
        if (name != null && name != room.name) {
            checkRoomNameAvailability(name)
        }
        
        return updateAndSaveRoom(room, name, description, maxParticipants)
    }

    /**
     * 채팅방을 삭제합니다. (소유자만 가능)
     */
    @Transactional
    suspend fun deleteChatRoom(roomId: Long, requesterId: Long) {
        validateRoomDeletionPermission(requesterId, roomId)
        
        // 모든 참여자 관계 삭제 후 채팅방 삭제
        userChatRoomService.deleteAllByChatRoomId(roomId)
        chatRoomRepository.deleteById(roomId).awaitSingleOrNull()
    }

    /**
     * 사용자가 채팅방에 참여합니다.
     */
    @Transactional
    suspend fun joinChatRoom(
        userId: Long,
        roomId: Long,
        invitedBy: Long? = null
    ): UserChatRoom {
        // 기존 관계 확인
        val existingRelationship = userChatRoomService.findUserChatRoomRelationship(userId, roomId).awaitSingleOrNull()
        
        if (existingRelationship != null) {
            return if (existingRelationship.isActive) {
                existingRelationship
            } else {
                userChatRoomService.rejoinChatRoom(userId, roomId)
            }
        }
        
        // 새로운 참여
        val room = validateRoomJoinEligibility(userId, roomId)
        val memberRole = if (invitedBy != null) {
            validateInvitePermission(invitedBy, roomId)
            ChatRoomRole.MEMBER
        } else {
            if (room.isPrivateRoom()) {
                throw BusinessLogicException("비공개 채팅방은 초대를 통해서만 참여할 수 있습니다.")
            } else {
                ChatRoomRole.MEMBER
            }
        }
        
        return userChatRoomService.joinChatRoom(userId, roomId, memberRole, invitedBy)
    }

    /**
     * 사용자가 채팅방을 떠납니다.
     */
    @Transactional
    suspend fun leaveChatRoom(userId: Long, roomId: Long) {
        logger.debug("Attempting to leave chat room - userId: $userId, roomId: $roomId")
        
        val relationship = userChatRoomService.getUserChatRoomRelationship(userId, roomId).awaitSingleOrNull()
        if (relationship == null) {
            logger.debug("No relationship found for userId: $userId, roomId: $roomId")
            throw BusinessLogicException("참여하지 않은 채팅방입니다.")
        }
        
        logger.debug("Found relationship - userId: $userId, roomId: $roomId, isActive: ${relationship.isActive}, leftAt: ${relationship.leftAt}, role: ${relationship.role}")
        
        when {
            !relationship.isActiveParticipant() -> {
                logger.info("User ${userId} already left room ${roomId}")
                return
            }
            relationship.role == ChatRoomRole.OWNER -> {
                logger.debug("User $userId is owner of room $roomId, handling owner leaving")
                handleOwnerLeaving(relationship)
            }
            else -> {
                logger.debug("User $userId is regular member of room $roomId, processing leave")
                userChatRoomService.leaveChatRoomWithRelationship(relationship)
            }
        }
    }

    /**
     * 채팅방 상세 정보를 조회합니다.
     */
    suspend fun getChatRoomDetails(roomId: Long, requesterId: Long? = null): ChatRoomDetails {
        val room = chatRoomRepository.findById(roomId).awaitSingleOrNull() 
            ?: throw ChatRoomNotFoundException("채팅방을 찾을 수 없습니다.")

        val participantCount = userChatRoomService.countActiveParticipants(roomId).awaitSingle().toInt()
        val participants = userChatRoomService.getChatRoomActiveParticipants(roomId).collectList().awaitSingle()
        
        val userRelationship = if (requesterId != null) {
            userChatRoomService.findUserChatRoomRelationship(requesterId, roomId).awaitSingleOrNull()
        } else {
            null
        }

        return ChatRoomDetails(
            room = room,
            participantCount = participantCount,
            participants = participants,
            userRelationship = userRelationship
        )
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
    private suspend fun validateUser(userId: Long): User {
        return userRepository.findById(userId).awaitSingleOrNull()
            ?: throw UserNotFoundException("사용자를 찾을 수 없습니다.")
    }

    /**
     * 채팅방 이름 중복을 검사합니다.
     */
    private suspend fun checkRoomNameAvailability(name: String) {
        val exists = chatRoomRepository.existsByName(name).awaitSingle()
        if (exists) {
            throw BusinessLogicException("이미 존재하는 채팅방 이름입니다.")
        }
    }

    /**
     * 채팅방을 생성하고 저장합니다.
     */
    private suspend fun createAndSaveChatRoom(
        creatorId: Long,
        name: String,
        description: String?,
        isPrivate: Boolean,
        maxParticipants: Int
    ): ChatRoom {
        val newRoom = ChatRoom(
            name = name,
            description = description,
            createdBy = creatorId,
            isPrivate = isPrivate,
            maxParticipants = maxParticipants,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        return chatRoomRepository.save(newRoom).awaitSingle()
    }

    /**
     * 채팅방 업데이트 권한을 검증하고 채팅방을 반환합니다.
     */
    private suspend fun validateRoomUpdatePermission(requesterId: Long, roomId: Long): ChatRoom {
        val relationship = getUserChatRoomRelationship(requesterId, roomId).awaitSingle()
        val room = chatRoomRepository.findById(roomId).awaitSingleOrNull()
            ?: throw ChatRoomNotFoundException("채팅방을 찾을 수 없습니다.")
        
        if (!relationship.canModifyRoomSettings()) {
            throw InsufficientPermissionException("채팅방 설정을 변경할 권한이 없습니다.")
        }
        
        return room
    }

    /**
     * 채팅방을 업데이트하고 저장합니다.
     */
    private suspend fun updateAndSaveRoom(
        room: ChatRoom,
        newName: String?,
        newDescription: String?,
        newMaxParticipants: Int?
    ): ChatRoom {
        val updatedRoom = room.updateInfo(newName, newDescription, newMaxParticipants)
        return chatRoomRepository.save(updatedRoom).awaitSingle()
    }

    /**
     * 채팅방 삭제 권한을 검증하고 채팅방을 반환합니다.
     */
    private suspend fun validateRoomDeletionPermission(requesterId: Long, roomId: Long): ChatRoom {
        val relationship = getUserChatRoomRelationship(requesterId, roomId).awaitSingle()
        val room = chatRoomRepository.findById(roomId).awaitSingleOrNull()
            ?: throw ChatRoomNotFoundException("채팅방을 찾을 수 없습니다.")
        
        if (!relationship.canDeleteRoom()) {
            throw InsufficientPermissionException("채팅방을 삭제할 권한이 없습니다.")
        }
        
        return room
    }

    /**
     * 채팅방 참여 자격을 검증합니다.
     */
    private suspend fun validateRoomJoinEligibility(userId: Long, roomId: Long): ChatRoom {
        validateUser(userId)
        val room = chatRoomRepository.findById(roomId).awaitSingleOrNull()
            ?: throw ChatRoomNotFoundException("채팅방을 찾을 수 없습니다.")
        val participantCount = userChatRoomService.countActiveParticipants(roomId).awaitSingle()

        if (room.maxParticipants > 0 && participantCount >= room.maxParticipants) {
            throw BusinessLogicException("채팅방이 가득 찼습니다.")
        }
        
        return room
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
    private suspend fun handleOwnerLeaving(ownerRelationship: UserChatRoom) {
        val ownerId = ownerRelationship.userId
        val roomId = ownerRelationship.chatRoomId
        
        // 다음 관리자 찾기
        val nextAdmin = userChatRoomService.getChatRoomAdmins(roomId)
            .filter { it.userId != ownerId && it.role == ChatRoomRole.ADMIN }
            .collectList()
            .awaitSingleOrNull()
            ?.firstOrNull()
        
        if (nextAdmin != null) {
            // 다음 관리자를 소유자로 승격
            userChatRoomService.changeUserRole(ownerId, nextAdmin.userId, roomId, ChatRoomRole.OWNER)
            userChatRoomService.leaveChatRoomWithRelationship(ownerRelationship)
        } else {
            // 관리자가 없으면 일반 멤버 중 한 명을 소유자로 승격
            val nextMember = userChatRoomService.getChatRoomActiveParticipants(roomId)
                .filter { it.userId != ownerId }
                .collectList()
                .awaitSingleOrNull()
                ?.firstOrNull()
            
            if (nextMember != null) {
                userChatRoomService.changeUserRole(ownerId, nextMember.userId, roomId, ChatRoomRole.OWNER)
                userChatRoomService.leaveChatRoomWithRelationship(ownerRelationship)
            } else {
                // 혼자 있는 경우 직접 삭제 (순환 참조 방지)
                userChatRoomService.leaveChatRoomWithRelationship(ownerRelationship)
                userChatRoomService.deleteAllByChatRoomId(roomId)
                chatRoomRepository.deleteById(roomId).awaitSingleOrNull()
            }
        }
    }

    /**
     * 사용자-채팅방 관계를 조회합니다.
     */
    private fun getUserChatRoomRelationship(userId: Long, roomId: Long): Mono<UserChatRoom> {
        return userChatRoomService.getUserChatRoomRelationship(userId, roomId)
    }

    // === 권한 검증 및 참여자 관리 기능 ===

    /**
     * 채팅방 참여자 목록을 조회합니다.
     * 요청자는 채팅방 참여자이거나 관리자여야 합니다.
     */
    fun getParticipants(roomId: Long, requesterId: Long): Flux<UserChatRoom> {
        return validateUserPermission(requesterId, roomId, ChatRoomRole.MEMBER)
            .flatMapMany { userChatRoomService.getChatRoomActiveParticipants(roomId) }
    }

    /**
     * 참여자 역할 변경 기능 (관리자/소유자만 가능)
     */
    @Transactional
    suspend fun changeParticipantRole(
        requesterId: Long, 
        targetUserId: Long, 
        roomId: Long, 
        newRole: ChatRoomRole
    ): UserChatRoom {
        validateUserPermission(requesterId, roomId, ChatRoomRole.ADMIN).awaitSingle()
        return userChatRoomService.changeUserRole(requesterId, targetUserId, roomId, newRole)
    }

    /**
     * 참여자 강제 퇴장 기능 (관리자/소유자만 가능)
     */
    @Transactional
    suspend fun kickParticipant(requesterId: Long, targetUserId: Long, roomId: Long) {
        validateUserPermission(requesterId, roomId, ChatRoomRole.ADMIN).awaitSingle()
        userChatRoomService.kickUserFromChatRoom(requesterId, targetUserId, roomId)
    }

    /**
     * 채팅방의 관리자 목록을 조회합니다.
     */
    fun getChatRoomAdmins(roomId: Long, requesterId: Long): Flux<UserChatRoom> {
        return validateUserPermission(requesterId, roomId, ChatRoomRole.MEMBER)
            .flatMapMany { userChatRoomService.getChatRoomAdmins(roomId) }
    }

    /**
     * 채팅방 소유자 정보를 조회합니다.
     */
    fun getChatRoomOwner(roomId: Long, requesterId: Long): Mono<UserChatRoom> {
        return validateUserPermission(requesterId, roomId, ChatRoomRole.MEMBER)
            .flatMap { userChatRoomService.getChatRoomOwner(roomId) }
    }

    /**
     * 사용자가 채팅방에서 특정 작업을 수행할 권한이 있는지 확인합니다.
     */
    fun hasPermissionForAction(userId: Long, roomId: Long, requiredRole: ChatRoomRole): Mono<Boolean> {
        return userChatRoomService.getUserChatRoomRelationship(userId, roomId)
            .map { relationship -> relationship.role.level >= requiredRole.level }
            .onErrorReturn(false)
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