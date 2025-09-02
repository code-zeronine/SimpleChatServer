package com.simplechat.infrastructure.repository

import com.simplechat.domain.entity.MessageType
import com.simplechat.infrastructure.entity.ChatMessageEntity
import org.springframework.data.domain.Pageable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDateTime

/**
 * 메시지 검색 전용 리포지토리 인터페이스
 * 
 * 복잡한 검색 조건과 전문 검색 기능을 제공합니다.
 * MongoDB의 텍스트 인덱스를 활용한 고성능 검색을 지원합니다.
 */
interface MessageSearchRepository {
    
    /**
     * 다중 조건 메시지 검색
     */
    fun searchMessages(
        roomId: Long?,
        keyword: String?,
        userId: Long?,
        messageType: MessageType?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
        pageable: Pageable
    ): Flux<ChatMessageEntity>
    
    /**
     * 검색 결과 개수 조회 (성능 최적화된 카운트 쿼리)
     */
    fun countSearchResults(
        roomId: Long?,
        keyword: String?,
        userId: Long?,
        messageType: MessageType?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?
    ): Mono<Long>
}