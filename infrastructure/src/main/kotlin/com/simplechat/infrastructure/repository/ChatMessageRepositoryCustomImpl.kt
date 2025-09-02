package com.simplechat.infrastructure.repository

import com.simplechat.domain.entity.MessageType
import com.simplechat.infrastructure.entity.ChatMessageEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.TextCriteria
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import java.time.LocalDateTime

@Repository
class ChatMessageRepositoryCustomImpl(
    private val mongoTemplate: ReactiveMongoTemplate
) : ChatMessageRepositoryCustom {

    override fun searchMessages(
        roomId: Long?,
        keyword: String?,
        userId: Long?,
        messageType: MessageType?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
        pageable: Pageable
    ): Flux<ChatMessageEntity> {
        val criteria = Criteria()
        val query = Query()

        roomId?.let { criteria.and("roomId").`is`(it) }
        userId?.let { criteria.and("userId").`is`(it) }
        messageType?.let { criteria.and("messageType").`is`(it) }

        if (startDate != null && endDate != null) {
            criteria.and("timestamp").gte(startDate).lte(endDate)
        } else if (startDate != null) {
            criteria.and("timestamp").gte(startDate)
        } else if (endDate != null) {
            criteria.and("timestamp").lte(endDate)
        }

        if (!keyword.isNullOrBlank()) {
            val textCriteria = TextCriteria.forDefaultLanguage().matching(keyword)
            query.addCriteria(textCriteria)
        }

        query.addCriteria(criteria)
        query.with(pageable)

        return mongoTemplate.find(query, ChatMessageEntity::class.java)
    }
}
