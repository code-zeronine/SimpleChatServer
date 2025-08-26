package com.simplechat.infrastructure.repository

import com.simplechat.domain.entity.ChatMessage
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface ChatMessageRepository : ReactiveMongoRepository<ChatMessage, String> {
    
    /**
     * 특정 채팅방의 메시지를 최신순으로 페이지네이션하여 조회
     */
    fun findByRoomIdOrderByTimestampDesc(roomId: Long, pageable: Pageable): Flux<ChatMessage>
    
    /**
     * 특정 채팅방의 총 메시지 개수 조회
     */
    fun countByRoomId(roomId: Long): Mono<Long>
    
    /**
     * 특정 사용자의 메시지 조회 (디버깅/관리 목적)
     */
    fun findByUserIdOrderByTimestampDesc(userId: Long, pageable: Pageable): Flux<ChatMessage>
    
    /**
     * 특정 날짜 이후의 채팅방 메시지 조회
     */
    fun findByRoomIdAndTimestampAfterOrderByTimestampAsc(
        roomId: Long, 
        timestamp: java.time.LocalDateTime
    ): Flux<ChatMessage>
}