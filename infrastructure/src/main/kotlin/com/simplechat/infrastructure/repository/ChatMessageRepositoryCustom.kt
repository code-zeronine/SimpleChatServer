package com.simplechat.infrastructure.repository

import com.simplechat.domain.entity.MessageType
import com.simplechat.infrastructure.entity.ChatMessageEntity
import org.springframework.data.domain.Pageable
import reactor.core.publisher.Flux
import java.time.LocalDateTime

interface ChatMessageRepositoryCustom {
    fun searchMessages(
        roomId: Long?,
        keyword: String?,
        userId: Long?,
        messageType: MessageType?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
        pageable: Pageable
    ): Flux<ChatMessageEntity>
}
