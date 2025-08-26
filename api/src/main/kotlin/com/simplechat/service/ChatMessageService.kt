package com.simplechat.service

import com.simplechat.domain.entity.ChatMessage
import com.simplechat.domain.entity.MessageType
import com.simplechat.infrastructure.entity.ChatMessageEntity
import com.simplechat.infrastructure.repository.ChatMessageRepository
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDateTime

/**
 * 채팅 메시지 Application 서비스
 * 
 * 채팅 메시지 관련 비즈니스 로직을 담당합니다.
 * Repository와 도메인 객체 간의 변환을 처리하고,
 * 시스템 메시지 생성 등의 Application 로직을 제공합니다.
 */
@Service
class ChatMessageService(
    private val repository: ChatMessageRepository
) {

    /**
     * 채팅 메시지를 저장합니다.
     */
    fun save(chatMessage: ChatMessage): Mono<ChatMessage> {
        // 도메인 검증 (시스템 메시지는 userId = 0이 허용됨)
        if (chatMessage.isSystemMessage()) {
            require(chatMessage.roomId > 0) { "Room ID must be positive: ${chatMessage.roomId}" }
            require(chatMessage.content.isNotBlank()) { "Content cannot be blank" }
            require(chatMessage.content.length <= 1000) { "Content cannot exceed 1000 characters" }
        } else {
            require(chatMessage.isValid()) { "Invalid chat message: ${chatMessage.getMessageInfo()}" }
        }
        
        val entity = ChatMessageEntity.fromDomain(chatMessage)
        return repository.save(entity)
            .map { it.toDomain() }
    }

    /**
     * 여러 채팅 메시지를 저장합니다.
     */
    fun saveAll(chatMessages: Iterable<ChatMessage>): Flux<ChatMessage> {
        // 모든 메시지 검증
        chatMessages.forEach { message ->
            if (message.isSystemMessage()) {
                require(message.roomId > 0) { "Room ID must be positive: ${message.roomId}" }
                require(message.content.isNotBlank()) { "Content cannot be blank" }
                require(message.content.length <= 1000) { "Content cannot exceed 1000 characters" }
            } else {
                require(message.isValid()) { "Invalid chat message: ${message.getMessageInfo()}" }
            }
        }
        
        val entities = chatMessages.map { ChatMessageEntity.fromDomain(it) }
        return repository.saveAll(entities)
            .map { it.toDomain() }
    }

    /**
     * ID로 채팅 메시지를 조회합니다.
     */
    fun findById(id: String): Mono<ChatMessage> {
        return repository.findById(id)
            .map { it.toDomain() }
    }

    /**
     * 특정 채팅방의 메시지를 최신순으로 페이지네이션하여 조회합니다.
     */
    fun findByRoomId(roomId: Long, pageable: Pageable): Flux<ChatMessage> {
        require(roomId > 0) { "Room ID must be positive: $roomId" }
        
        return repository.findByRoomIdOrderByTimestampDesc(roomId, pageable)
            .map { it.toDomain() }
    }

    /**
     * 특정 채팅방의 총 메시지 개수를 조회합니다.
     */
    fun countByRoomId(roomId: Long): Mono<Long> {
        require(roomId > 0) { "Room ID must be positive: $roomId" }
        
        return repository.countByRoomId(roomId)
    }

    /**
     * 특정 사용자의 메시지를 조회합니다.
     */
    fun findByUserId(userId: Long, pageable: Pageable): Flux<ChatMessage> {
        require(userId > 0) { "User ID must be positive: $userId" }
        
        return repository.findByUserIdOrderByTimestampDesc(userId, pageable)
            .map { it.toDomain() }
    }

    /**
     * 특정 날짜 이후의 채팅방 메시지를 조회합니다.
     */
    fun findByRoomIdAfterTimestamp(
        roomId: Long, 
        timestamp: LocalDateTime
    ): Flux<ChatMessage> {
        require(roomId > 0) { "Room ID must be positive: $roomId" }
        
        return repository.findByRoomIdAndTimestampAfterOrderByTimestampAsc(roomId, timestamp)
            .map { it.toDomain() }
    }

    /**
     * 특정 날짜 이전의 채팅방 메시지를 조회합니다. (이전 메시지 로딩)
     */
    fun findByRoomIdBeforeTimestamp(
        roomId: Long,
        timestamp: LocalDateTime,
        pageable: Pageable
    ): Flux<ChatMessage> {
        require(roomId > 0) { "Room ID must be positive: $roomId" }
        
        return repository.findByRoomIdAndTimestampBeforeOrderByTimestampDesc(roomId, timestamp, pageable)
            .map { it.toDomain() }
    }

    /**
     * 특정 메시지 타입의 메시지를 조회합니다.
     */
    fun findByRoomIdAndMessageType(
        roomId: Long,
        messageType: MessageType,
        pageable: Pageable
    ): Flux<ChatMessage> {
        require(roomId > 0) { "Room ID must be positive: $roomId" }
        
        return repository.findByRoomIdAndMessageTypeOrderByTimestampDesc(roomId, messageType, pageable)
            .map { it.toDomain() }
    }

    /**
     * 특정 채팅방에서 특정 사용자의 메시지 개수를 조회합니다.
     */
    fun countByRoomIdAndUserId(roomId: Long, userId: Long): Mono<Long> {
        require(roomId > 0) { "Room ID must be positive: $roomId" }
        require(userId > 0) { "User ID must be positive: $userId" }
        
        return repository.countByRoomIdAndUserId(roomId, userId)
    }

    /**
     * 특정 기간 내의 채팅방 메시지를 조회합니다.
     */
    fun findByRoomIdBetweenTimestamp(
        roomId: Long,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): Flux<ChatMessage> {
        require(roomId > 0) { "Room ID must be positive: $roomId" }
        require(startTime.isBefore(endTime)) { "Start time must be before end time" }
        
        return repository.findByRoomIdAndTimestampBetween(roomId, startTime, endTime)
            .map { it.toDomain() }
    }

    /**
     * 특정 채팅방의 최신 메시지를 조회합니다.
     */
    fun findLatestByRoomId(roomId: Long): Mono<ChatMessage> {
        require(roomId > 0) { "Room ID must be positive: $roomId" }
        
        return repository.findTopByRoomIdOrderByTimestampDesc(roomId)
            .map { it.toDomain() }
    }

    /**
     * 시스템 메시지를 생성합니다.
     */
    fun createSystemMessage(
        roomId: Long,
        content: String,
        messageType: MessageType = MessageType.SYSTEM
    ): Mono<ChatMessage> {
        require(roomId > 0) { "Room ID must be positive: $roomId" }
        require(content.isNotBlank()) { "Content cannot be blank" }
        require(messageType != MessageType.TEXT) { "System message cannot be TEXT type" }
        
        val systemMessage = ChatMessage(
            roomId = roomId,
            userId = 0L, // 시스템 사용자 ID
            content = content,
            timestamp = LocalDateTime.now(),
            messageType = messageType
        )
        
        return save(systemMessage)
    }

    /**
     * 사용자 참여 메시지를 생성합니다.
     */
    fun createJoinMessage(roomId: Long, userId: Long, userNickname: String): Mono<ChatMessage> {
        return createSystemMessage(
            roomId = roomId,
            content = "$userNickname 님이 채팅방에 참여했습니다.",
            messageType = MessageType.JOIN
        )
    }

    /**
     * 사용자 퇴장 메시지를 생성합니다.
     */
    fun createLeaveMessage(roomId: Long, userId: Long, userNickname: String): Mono<ChatMessage> {
        return createSystemMessage(
            roomId = roomId,
            content = "$userNickname 님이 채팅방을 나갔습니다.",
            messageType = MessageType.LEAVE
        )
    }

    /**
     * 모든 메시지를 조회합니다.
     */
    fun findAll(): Flux<ChatMessage> {
        return repository.findAll()
            .map { it.toDomain() }
    }

    /**
     * 메시지를 삭제합니다.
     */
    fun deleteById(id: String): Mono<Void> {
        return repository.deleteById(id)
    }

    /**
     * 모든 메시지를 삭제합니다.
     */
    fun deleteAll(): Mono<Void> {
        return repository.deleteAll()
    }

    /**
     * 전체 메시지 수를 조회합니다.
     */
    fun count(): Mono<Long> {
        return repository.count()
    }
}