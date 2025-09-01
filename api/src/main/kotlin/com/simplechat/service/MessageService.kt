package com.simplechat.service

import com.simplechat.dto.MessageDto
import com.simplechat.dto.PagedApiResponse
import com.simplechat.dto.PaginationInfo
import com.simplechat.infrastructure.entity.ChatMessageEntity
import com.simplechat.infrastructure.repository.ChatMessageRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.ZoneOffset

@Service
class MessageService(
    private val chatMessageRepository: ChatMessageRepository
) {

    fun getMessagesByRoom(roomId: String, page: Int, size: Int): Mono<PagedApiResponse<MessageDto>> {
        val pageable = PageRequest.of(page, size)
        val messagesFlux = chatMessageRepository.findByRoomIdOrderByTimestampDesc(roomId.toLong(), pageable)
            .map { it.toDto() }
        val totalMessagesMono = chatMessageRepository.countByRoomId(roomId.toLong())

        return Mono.zip(messagesFlux.collectList(), totalMessagesMono)
            .map { tuple ->
                val messages = tuple.t1
                val totalMessages = tuple.t2
                val totalPages = if (size > 0) (totalMessages + size - 1) / size else 0
                val paginationInfo = PaginationInfo(
                    page = page,
                    size = size,
                    totalElements = totalMessages,
                    totalPages = totalPages.toInt(),
                    hasNext = page < totalPages - 1,
                    hasPrevious = page > 0
                )
                PagedApiResponse.success(messages, paginationInfo)
            }
    }

    fun getRecentMessages(roomId: String, size: Int): Flux<MessageDto> {
        return chatMessageRepository.findByRoomIdOrderByTimestampDesc(roomId.toLong(), PageRequest.of(0, size))
            .map { it.toDto() }
    }

    fun countMessages(roomId: String): Mono<Long> {
        return chatMessageRepository.countByRoomId(roomId.toLong())
    }

    private fun ChatMessageEntity.toDto(): MessageDto {
        return MessageDto(
            id = this.id,
            roomId = this.roomId.toString(),
            userId = this.userId,
            content = this.content,
            timestamp = this.timestamp.toInstant(ZoneOffset.UTC)
        )
    }
}
