package com.simplechat.infrastructure.repository

import com.simplechat.domain.entity.MessageType
import com.simplechat.infrastructure.entity.ChatMessageEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.Query
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDateTime

interface ChatMessageMongoRepository : ReactiveMongoRepository<ChatMessageEntity, String>, MessageSearchRepository {
    
    /**
     * 특정 채팅방의 메시지를 최신순으로 페이지네이션하여 조회
     */
    fun findByRoomIdOrderByTimestampDesc(roomId: Long, pageable: Pageable): Flux<ChatMessageEntity>
    
    /**
     * 특정 채팅방의 총 메시지 개수 조회
     */
    fun countByRoomId(roomId: Long): Mono<Long>
    
    /**
     * 특정 사용자의 메시지 조회 (디버깅/관리 목적)
     */
    fun findByUserIdOrderByTimestampDesc(userId: Long, pageable: Pageable): Flux<ChatMessageEntity>
    
    /**
     * 특정 날짜 이후의 채팅방 메시지 조회
     */
    fun findByRoomIdAndTimestampAfterOrderByTimestampAsc(
        roomId: Long, 
        timestamp: LocalDateTime
    ): Flux<ChatMessageEntity>
    
    /**
     * 특정 날짜 이전의 채팅방 메시지 조회 (이전 메시지 로딩)
     */
    fun findByRoomIdAndTimestampBeforeOrderByTimestampDesc(
        roomId: Long, 
        timestamp: LocalDateTime,
        pageable: Pageable
    ): Flux<ChatMessageEntity>
    
    /**
     * 특정 메시지 타입의 메시지 조회
     */
    fun findByRoomIdAndMessageTypeOrderByTimestampDesc(
        roomId: Long, 
        messageType: MessageType,
        pageable: Pageable
    ): Flux<ChatMessageEntity>
    
    /**
     * 특정 채팅방에서 특정 사용자의 메시지 개수 조회
     */
    fun countByRoomIdAndUserId(roomId: Long, userId: Long): Mono<Long>
    
    /**
     * 특정 기간 내의 채팅방 메시지 조회
     */
    @Query("{ 'roomId': ?0, 'timestamp': { \$gte: ?1, \$lte: ?2 } }")
    fun findByRoomIdAndTimestampBetween(
        roomId: Long, 
        startTime: LocalDateTime, 
        endTime: LocalDateTime
    ): Flux<ChatMessageEntity>
    
    /**
     * 특정 채팅방의 최신 메시지 조회
     */
    fun findTopByRoomIdOrderByTimestampDesc(roomId: Long): Mono<ChatMessageEntity>

    /**
     * 텍스트 검색 및 필터링
     */
    fun findAllBy(criteria: org.springframework.data.mongodb.core.query.TextCriteria, pageable: Pageable): Flux<ChatMessageEntity>
}