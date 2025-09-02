package com.simplechat.service

import com.simplechat.dto.MessageDto
import com.simplechat.dto.PagedApiResponse
import com.simplechat.dto.PaginationInfo
import com.simplechat.domain.entity.ChatMessage
import com.simplechat.domain.entity.MessageType
import com.simplechat.domain.repository.ChatMessageRepository
import com.simplechat.util.SearchHighlighter
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.ZoneOffset

@Service
class MessageService(
    private val chatMessageRepository: ChatMessageRepository,
    private val searchHighlighter: SearchHighlighter
) {

    fun getMessagesByRoom(roomId: String, page: Int, size: Int): Mono<PagedApiResponse<MessageDto>> {
        val messagesFlux = chatMessageRepository.findByRoomIdOrderByTimestampDesc(roomId.toLong(), page, size)
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
        return chatMessageRepository.findRecentByRoomId(roomId.toLong(), size)
            .map { it.toDto() }
    }

    fun countMessages(roomId: String): Mono<Long> {
        return chatMessageRepository.countByRoomId(roomId.toLong())
    }

    fun searchMessages(
        roomId: String?,
        keyword: String?,
        userId: String?,
        messageType: String?,
        startDate: String?,
        endDate: String?,
        page: Int,
        size: Int
    ): Mono<PagedApiResponse<MessageDto>> {
        val messageTypeEnum = messageType?.let { MessageType.valueOf(it.uppercase()) }
        val startDateTime = startDate?.let { java.time.LocalDateTime.parse(it) }
        val endDateTime = endDate?.let { java.time.LocalDateTime.parse(it) }

        val messagesFlux = chatMessageRepository.searchMessages(
            roomId = roomId?.toLong(),
            keyword = keyword,
            userId = userId?.toLong(),
            messageType = messageTypeEnum,
            startDate = startDateTime,
            endDate = endDateTime,
            page = page,
            size = size
        ).map { it.toDto(keyword) }

        val totalCountMono = chatMessageRepository.countSearchResults(
            roomId = roomId?.toLong(),
            keyword = keyword,
            userId = userId?.toLong(),
            messageType = messageTypeEnum,
            startDate = startDateTime,
            endDate = endDateTime
        )

        return Mono.zip(messagesFlux.collectList(), totalCountMono)
            .map { tuple ->
                val messages = tuple.t1
                val totalResults = tuple.t2
                val totalPages = if (size > 0) (totalResults + size - 1) / size else 0
                val paginationInfo = PaginationInfo(
                    page = page,
                    size = size,
                    totalElements = totalResults,
                    totalPages = totalPages.toInt(),
                    hasNext = page < totalPages - 1,
                    hasPrevious = page > 0
                )
                PagedApiResponse.success(messages, paginationInfo)
            }
    }

    private fun ChatMessage.toDto(searchKeyword: String? = null): MessageDto {
        return MessageDto(
            id = this.id,
            roomId = this.roomId.toString(),
            userId = this.userId,
            content = this.content,
            timestamp = this.timestamp.toInstant(ZoneOffset.UTC),
            highlightedContent = searchHighlighter.highlightKeyword(this.content, searchKeyword),
            messageType = this.messageType.name
        )
    }
}
