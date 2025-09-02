package com.simplechat.infrastructure.repository

import com.simplechat.domain.entity.ChatMessage
import com.simplechat.domain.entity.MessageType
import com.simplechat.domain.repository.ChatMessageRepository
import com.simplechat.infrastructure.entity.ChatMessageEntity
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDateTime

/**
 * 채팅 메시지 도메인 리포지토리의 MongoDB 구현체
 * 
 * Clean Architecture의 의존성 역전 원칙을 따라
 * 도메인 인터페이스를 구현하고 Infrastructure Entity와 Domain Entity 간의
 * 변환을 담당합니다.
 */
@Repository
class ChatMessageRepositoryImpl(
    private val mongoRepository: ChatMessageMongoRepository
) : ChatMessageRepository {

    override fun save(chatMessage: ChatMessage): Mono<ChatMessage> {
        val entity = ChatMessageEntity.fromDomain(chatMessage)
        return mongoRepository.save(entity)
            .map { it.toDomain() }
    }

    override fun findById(id: String): Mono<ChatMessage> {
        return mongoRepository.findById(id)
            .map { it.toDomain() }
    }

    override fun findByRoomIdOrderByTimestampDesc(roomId: Long, page: Int, size: Int): Flux<ChatMessage> {
        val pageable = PageRequest.of(page, size)
        return mongoRepository.findByRoomIdOrderByTimestampDesc(roomId, pageable)
            .map { it.toDomain() }
    }

    override fun countByRoomId(roomId: Long): Mono<Long> {
        return mongoRepository.countByRoomId(roomId)
    }

    override fun findRecentByRoomId(roomId: Long, size: Int): Flux<ChatMessage> {
        val pageable = PageRequest.of(0, size)
        return mongoRepository.findByRoomIdOrderByTimestampDesc(roomId, pageable)
            .map { it.toDomain() }
    }

    override fun findByUserIdOrderByTimestampDesc(userId: Long, page: Int, size: Int): Flux<ChatMessage> {
        val pageable = PageRequest.of(page, size)
        return mongoRepository.findByUserIdOrderByTimestampDesc(userId, pageable)
            .map { it.toDomain() }
    }

    override fun findByRoomIdAndTimestampAfter(roomId: Long, timestamp: LocalDateTime): Flux<ChatMessage> {
        return mongoRepository.findByRoomIdAndTimestampAfterOrderByTimestampAsc(roomId, timestamp)
            .map { it.toDomain() }
    }

    override fun findByRoomIdAndTimestampBefore(
        roomId: Long, 
        timestamp: LocalDateTime, 
        page: Int, 
        size: Int
    ): Flux<ChatMessage> {
        val pageable = PageRequest.of(page, size)
        return mongoRepository.findByRoomIdAndTimestampBeforeOrderByTimestampDesc(roomId, timestamp, pageable)
            .map { it.toDomain() }
    }

    override fun findByRoomIdAndMessageType(
        roomId: Long, 
        messageType: MessageType, 
        page: Int, 
        size: Int
    ): Flux<ChatMessage> {
        val pageable = PageRequest.of(page, size)
        return mongoRepository.findByRoomIdAndMessageTypeOrderByTimestampDesc(roomId, messageType, pageable)
            .map { it.toDomain() }
    }

    override fun countByRoomIdAndUserId(roomId: Long, userId: Long): Mono<Long> {
        return mongoRepository.countByRoomIdAndUserId(roomId, userId)
    }

    override fun findByRoomIdAndTimestampBetween(
        roomId: Long, 
        startTime: LocalDateTime, 
        endTime: LocalDateTime
    ): Flux<ChatMessage> {
        return mongoRepository.findByRoomIdAndTimestampBetween(roomId, startTime, endTime)
            .map { it.toDomain() }
    }

    override fun findLatestByRoomId(roomId: Long): Mono<ChatMessage> {
        return mongoRepository.findTopByRoomIdOrderByTimestampDesc(roomId)
            .map { it.toDomain() }
    }

    override fun searchMessages(
        roomId: Long?,
        keyword: String?,
        userId: Long?,
        messageType: MessageType?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
        page: Int,
        size: Int
    ): Flux<ChatMessage> {
        val pageable = PageRequest.of(page, size)
        return mongoRepository.searchMessages(
            roomId = roomId,
            keyword = keyword,
            userId = userId,
            messageType = messageType,
            startDate = startDate,
            endDate = endDate,
            pageable = pageable
        ).map { it.toDomain() }
    }

    override fun countSearchResults(
        roomId: Long?,
        keyword: String?,
        userId: Long?,
        messageType: MessageType?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?
    ): Mono<Long> {
        // MongoDB의 Custom Repository에서 카운트 기능을 구현해야 합니다
        // 일단 기본 구현으로 검색 후 카운트
        return mongoRepository.searchMessages(
            roomId = roomId,
            keyword = keyword,
            userId = userId,
            messageType = messageType,
            startDate = startDate,
            endDate = endDate,
            pageable = PageRequest.of(0, Int.MAX_VALUE)
        ).count()
    }
}