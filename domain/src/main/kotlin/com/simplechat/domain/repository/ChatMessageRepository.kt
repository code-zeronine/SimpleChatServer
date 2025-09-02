package com.simplechat.domain.repository

import com.simplechat.domain.entity.ChatMessage
import com.simplechat.domain.entity.MessageType
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDateTime

/**
 * 채팅 메시지 도메인 리포지토리 인터페이스
 * 
 * Clean Architecture의 의존성 역전 원칙을 따라
 * 도메인 계층에서 인터페이스를 정의하고 
 * 인프라스트럭처 계층에서 구현합니다.
 */
interface ChatMessageRepository {
    
    /**
     * 메시지를 저장합니다.
     */
    fun save(chatMessage: ChatMessage): Mono<ChatMessage>
    
    /**
     * ID로 메시지를 조회합니다.
     */
    fun findById(id: String): Mono<ChatMessage>
    
    /**
     * 특정 채팅방의 메시지를 최신순으로 페이지네이션하여 조회합니다.
     */
    fun findByRoomIdOrderByTimestampDesc(roomId: Long, page: Int, size: Int): Flux<ChatMessage>
    
    /**
     * 특정 채팅방의 총 메시지 개수를 조회합니다.
     */
    fun countByRoomId(roomId: Long): Mono<Long>
    
    /**
     * 특정 채팅방의 최근 메시지를 조회합니다.
     */
    fun findRecentByRoomId(roomId: Long, size: Int): Flux<ChatMessage>
    
    /**
     * 특정 사용자의 메시지를 조회합니다.
     */
    fun findByUserIdOrderByTimestampDesc(userId: Long, page: Int, size: Int): Flux<ChatMessage>
    
    /**
     * 특정 날짜 이후의 채팅방 메시지를 조회합니다.
     */
    fun findByRoomIdAndTimestampAfter(
        roomId: Long, 
        timestamp: LocalDateTime
    ): Flux<ChatMessage>
    
    /**
     * 특정 날짜 이전의 채팅방 메시지를 조회합니다.
     */
    fun findByRoomIdAndTimestampBefore(
        roomId: Long, 
        timestamp: LocalDateTime,
        page: Int,
        size: Int
    ): Flux<ChatMessage>
    
    /**
     * 특정 메시지 타입의 메시지를 조회합니다.
     */
    fun findByRoomIdAndMessageType(
        roomId: Long, 
        messageType: MessageType,
        page: Int,
        size: Int
    ): Flux<ChatMessage>
    
    /**
     * 특정 채팅방에서 특정 사용자의 메시지 개수를 조회합니다.
     */
    fun countByRoomIdAndUserId(roomId: Long, userId: Long): Mono<Long>
    
    /**
     * 특정 기간 내의 채팅방 메시지를 조회합니다.
     */
    fun findByRoomIdAndTimestampBetween(
        roomId: Long, 
        startTime: LocalDateTime, 
        endTime: LocalDateTime
    ): Flux<ChatMessage>
    
    /**
     * 특정 채팅방의 최신 메시지를 조회합니다.
     */
    fun findLatestByRoomId(roomId: Long): Mono<ChatMessage>
    
    /**
     * 고급 검색 기능 - 키워드 및 다양한 필터로 메시지를 검색합니다.
     */
    fun searchMessages(
        roomId: Long? = null,
        keyword: String? = null,
        userId: Long? = null,
        messageType: MessageType? = null,
        startDate: LocalDateTime? = null,
        endDate: LocalDateTime? = null,
        page: Int = 0,
        size: Int = 50
    ): Flux<ChatMessage>
    
    /**
     * 검색 결과의 총 개수를 반환합니다.
     */
    fun countSearchResults(
        roomId: Long? = null,
        keyword: String? = null,
        userId: Long? = null,
        messageType: MessageType? = null,
        startDate: LocalDateTime? = null,
        endDate: LocalDateTime? = null
    ): Mono<Long>
}