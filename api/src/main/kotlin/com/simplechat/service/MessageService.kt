package com.simplechat.service

import com.simplechat.domain.entity.ChatMessage
import com.simplechat.dto.MessageDto
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

    fun getMessagesByRoom(roomId: String, page: Int, size: Int): Flux<MessageDto> {
        return chatMessageRepository.findByRoomIdOrderByTimestampDesc(roomId.toLong(), PageRequest.of(page, size))
            .map { it.toDto() }
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
