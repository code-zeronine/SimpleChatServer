package com.simplechat.service

import com.simplechat.dto.MessageDto
import com.simplechat.dto.PagedApiResponse
import com.simplechat.dto.PaginationInfo
import com.simplechat.domain.entity.ChatMessage
import com.simplechat.domain.entity.MessageType
import com.simplechat.domain.repository.ChatMessageRepository
import com.simplechat.infrastructure.service.MessageCacheService
import com.simplechat.util.SearchHighlighter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.ZoneOffset

@Service
class MessageService(
    private val chatMessageRepository: ChatMessageRepository,
    private val messageCacheService: MessageCacheService,
    private val searchHighlighter: SearchHighlighter
) {
    
    private val logger = LoggerFactory.getLogger(MessageService::class.java)

    fun getMessagesByRoom(roomId: String, page: Int, size: Int): Mono<PagedApiResponse<MessageDto>> {
        val roomIdLong = roomId.toLong()
        
        // 첫 페이지이고 기본 사이즈인 경우 캐시 먼저 확인
        val messagesFlux = if (page == 0 && size <= 50) {
            messageCacheService.getRecentMessages(roomIdLong, size)
                .map { it.toDto() }
                .switchIfEmpty(
                    chatMessageRepository.findByRoomIdOrderByTimestampDesc(roomIdLong, page, size)
                        .map { it.toDto() }
                        .collectList()
                        .flatMapMany { messages ->
                            // 캐시에 저장
                            val domainMessages = messages.map { dto -> 
                                ChatMessage(
                                    id = dto.id,
                                    roomId = dto.roomId.toLong(),
                                    userId = dto.userId,
                                    content = dto.content,
                                    messageType = MessageType.valueOf(dto.messageType ?: "TEXT"),
                                    timestamp = java.time.LocalDateTime.ofInstant(dto.timestamp, ZoneOffset.UTC)
                                )
                            }
                            messageCacheService.cacheRecentMessages(roomIdLong, domainMessages)
                                .thenMany(Flux.fromIterable(messages))
                        }
                )
        } else {
            // 페이지네이션이 있는 경우는 직접 DB 조회
            chatMessageRepository.findByRoomIdOrderByTimestampDesc(roomIdLong, page, size)
                .map { it.toDto() }
        }
        
        // 메시지 개수는 캐시에서 먼저 확인
        val totalMessagesMono = messageCacheService.getCachedMessageCount(roomIdLong)
            .switchIfEmpty(
                chatMessageRepository.countByRoomId(roomIdLong)
                    .flatMap { count ->
                        messageCacheService.cacheMessageCount(roomIdLong, count)
                            .thenReturn(count)
                    }
            )

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
            .doOnSuccess {
                logger.debug("Fetched messages for room: {}, page: {}, size: {}", roomId, page, size)
            }
    }

    fun getRecentMessages(roomId: String, size: Int): Flux<MessageDto> {
        val roomIdLong = roomId.toLong()
        
        return messageCacheService.getRecentMessages(roomIdLong, size)
            .map { it.toDto() }
            .switchIfEmpty(
                chatMessageRepository.findRecentByRoomId(roomIdLong, size)
                    .map { it.toDto() }
                    .collectList()
                    .flatMapMany { messages ->
                        // 캐시에 저장
                        val domainMessages = messages.map { dto -> 
                            ChatMessage(
                                id = dto.id,
                                roomId = dto.roomId.toLong(),
                                userId = dto.userId,
                                content = dto.content,
                                messageType = MessageType.valueOf(dto.messageType ?: "TEXT"),
                                timestamp = java.time.LocalDateTime.ofInstant(dto.timestamp, ZoneOffset.UTC)
                            )
                        }
                        messageCacheService.cacheRecentMessages(roomIdLong, domainMessages)
                            .thenMany(Flux.fromIterable(messages))
                    }
            )
            .doOnComplete {
                logger.debug("Fetched recent messages for room: {}, size: {}", roomId, size)
            }
    }

    fun countMessages(roomId: String): Mono<Long> {
        val roomIdLong = roomId.toLong()
        
        return messageCacheService.getCachedMessageCount(roomIdLong)
            .switchIfEmpty(
                chatMessageRepository.countByRoomId(roomIdLong)
                    .flatMap { count ->
                        messageCacheService.cacheMessageCount(roomIdLong, count)
                            .thenReturn(count)
                    }
            )
            .doOnNext { count ->
                logger.debug("Fetched message count for room: {}, count: {}", roomId, count)
            }
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

    /**
     * 새 메시지 저장 후 캐시 업데이트
     */
    fun saveMessageAndUpdateCache(message: ChatMessage): Mono<ChatMessage> {
        // 실제 저장은 도메인 서비스에서 처리되므로 여기서는 캐시 업데이트만
        return messageCacheService.addNewMessageToCache(message.roomId, message)
            .then(messageCacheService.incrementMessageCount(message.roomId))
            .thenReturn(message)
            .doOnSuccess {
                logger.debug("Updated cache for new message: roomId={}, messageId={}", message.roomId, message.id)
            }
    }

    /**
     * 캐시 무효화
     */
    fun invalidateRoomCache(roomId: String): Mono<Void> {
        return messageCacheService.invalidateRoomCache(roomId.toLong())
            .doOnSuccess {
                logger.debug("Invalidated cache for room: {}", roomId)
            }
    }

    /**
     * 캐시 통계 조회
     */
    fun getCacheStats(roomId: String): Mono<MessageCacheService.CacheStats> {
        return messageCacheService.getCacheStats(roomId.toLong())
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
