package com.simplechat.infrastructure.repository

import com.simplechat.domain.entity.MessageType
import com.simplechat.infrastructure.entity.ChatMessageEntity
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.TextCriteria
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDateTime

/**
 * 메시지 검색 리포지토리 MongoDB 구현체
 * 
 * MongoDB의 텍스트 인덱스와 복합 인덱스를 활용하여
 * 고성능 메시지 검색 기능을 제공합니다.
 */
@Repository
class MessageSearchRepositoryImpl(
    private val mongoTemplate: ReactiveMongoTemplate
) : MessageSearchRepository {

    private val logger = LoggerFactory.getLogger(MessageSearchRepositoryImpl::class.java)

    override fun searchMessages(
        roomId: Long?,
        keyword: String?,
        userId: Long?,
        messageType: MessageType?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
        pageable: Pageable
    ): Flux<ChatMessageEntity> {
        val query = buildSearchQuery(roomId, keyword, userId, messageType, startDate, endDate)
        query.with(pageable)

        return mongoTemplate.find(query, ChatMessageEntity::class.java)
            .doOnSubscribe { 
                logger.debug("Executing search query: roomId={}, keyword={}, userId={}, messageType={}", 
                           roomId, keyword, userId, messageType)
            }
            .doOnError { error ->
                logger.error("Search query failed: {}", error.message, error)
            }
    }

    override fun countSearchResults(
        roomId: Long?,
        keyword: String?,
        userId: Long?,
        messageType: MessageType?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?
    ): Mono<Long> {
        val query = buildSearchQuery(roomId, keyword, userId, messageType, startDate, endDate)
        
        return mongoTemplate.count(query, ChatMessageEntity::class.java)
            .doOnNext { count ->
                logger.debug("Search count result: {}, roomId={}, keyword={}", count, roomId, keyword)
            }
            .doOnError { error ->
                logger.error("Search count failed: {}", error.message, error)
            }
    }

    /**
     * 검색 조건에 따른 MongoDB 쿼리 빌더
     * 
     * 텍스트 검색과 필터 조건을 조합하여 최적화된 쿼리를 생성합니다.
     */
    private fun buildSearchQuery(
        roomId: Long?,
        keyword: String?,
        userId: Long?,
        messageType: MessageType?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?
    ): Query {
        val criteria = Criteria()
        val query = Query()

        // 기본 필터 조건들
        roomId?.let { criteria.and("roomId").`is`(it) }
        userId?.let { criteria.and("userId").`is`(it) }
        messageType?.let { criteria.and("messageType").`is`(it) }

        // 날짜 범위 필터링 (인덱스 활용)
        when {
            startDate != null && endDate != null -> {
                criteria.and("timestamp").gte(startDate).lte(endDate)
            }
            startDate != null -> {
                criteria.and("timestamp").gte(startDate)
            }
            endDate != null -> {
                criteria.and("timestamp").lte(endDate)
            }
        }

        // MongoDB 텍스트 인덱스를 활용한 전문 검색
        if (!keyword.isNullOrBlank()) {
            val textCriteria = TextCriteria.forDefaultLanguage()
                .matching(keyword)
            query.addCriteria(textCriteria)
        }

        query.addCriteria(criteria)
        return query
    }
}