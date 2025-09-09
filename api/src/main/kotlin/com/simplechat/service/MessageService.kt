package com.simplechat.service

import com.simplechat.domain.entity.ChatMessage
import com.simplechat.domain.entity.MessageType
import com.simplechat.domain.repository.ChatMessageRepository
import com.simplechat.domain.repository.UserRepository
import com.simplechat.dto.MessageDto
import com.simplechat.dto.PagedApiResponse
import com.simplechat.dto.PaginationInfo
import com.simplechat.infrastructure.service.MessageCacheService
import com.simplechat.util.SearchHighlighter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.ZoneOffset

@Service
class MessageService(
    private val chatMessageRepository: ChatMessageRepository,
    private val userRepository: UserRepository,
    private val messageCacheService: MessageCacheService,
    private val searchHighlighter: SearchHighlighter
) {
    
    private val logger = LoggerFactory.getLogger(MessageService::class.java)

    suspend fun getMessagesByRoom(roomId: Long, page: Int, size: Int): PagedApiResponse<MessageDto> = coroutineScope {
        
        val messagesDeferred = async {
            if (page == 0 && size <= 50) {
                messageCacheService.getRecentMessages(roomId, size)
                    .map { it.toDto() }
                    .collectList()
                    .awaitSingleOrNull()
                    ?.ifEmpty { 
                        val dbMessages = chatMessageRepository.findByRoomIdOrderByTimestampDesc(roomId, page, size)
                            .map { it.toDto() }
                            .collectList()
                            .awaitSingleOrNull() ?: emptyList()
                        
                        if (dbMessages.isNotEmpty()) {
                            val domainMessages = dbMessages.map { dto -> 
                                ChatMessage(
                                    id = dto.id,
                                    roomId = dto.roomId.toLong(),
                                    userId = dto.userId,
                                    content = dto.content,
                                    messageType = MessageType.valueOf(dto.messageType ?: "TEXT"),
                                    timestamp = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(dto.timestamp), ZoneOffset.UTC)
                                )
                            }
                            messageCacheService.cacheRecentMessages(roomId, domainMessages).awaitSingleOrNull()
                        }
                        dbMessages
                    } ?: emptyList()
            } else {
                chatMessageRepository.findByRoomIdOrderByTimestampDesc(roomId, page, size)
                    .map { it.toDto() }
                    .collectList()
                    .awaitSingleOrNull() ?: emptyList()
            }
        }
        
        val totalMessagesDeferred = async {
            messageCacheService.getCachedMessageCount(roomId)
                .awaitSingleOrNull()
                ?: chatMessageRepository.countByRoomId(roomId)
                    .flatMap { count ->
                        messageCacheService.cacheMessageCount(roomId, count)
                            .thenReturn(count)
                    }
                    .awaitSingleOrNull() ?: 0L
        }

        val messages = messagesDeferred.await()
        val totalMessages = totalMessagesDeferred.await()
        createPagedApiResponse(messages, page, size, totalMessages)
    }

    suspend fun getRecentMessages(roomId: Long, size: Int): List<MessageDto> {
        
        val cachedMessages = messageCacheService.getRecentMessages(roomId, size)
            .map { it.toDto() }
            .collectList()
            .awaitSingleOrNull()

        if (cachedMessages != null && cachedMessages.isNotEmpty()) {
            return cachedMessages
        }

        val dbMessages = chatMessageRepository.findRecentByRoomId(roomId, size)
            .map { it.toDto() }
            .collectList()
            .awaitSingleOrNull() ?: emptyList()

        if (dbMessages.isNotEmpty()) {
            val domainMessages = dbMessages.map { dto -> 
                ChatMessage(
                    id = dto.id,
                    roomId = dto.roomId.toLong(),
                    userId = dto.userId,
                    content = dto.content,
                    messageType = MessageType.valueOf(dto.messageType ?: "TEXT"),
                    timestamp = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(dto.timestamp), ZoneOffset.UTC)
                )
            }
            messageCacheService.cacheRecentMessages(roomId, domainMessages).awaitSingleOrNull()
        }
        
        logger.debug("Fetched recent messages for room: {}, size: {}", roomId, size)
        return dbMessages
    }

    suspend fun countMessages(roomId: Long): Long {
        
        return messageCacheService.getCachedMessageCount(roomId)
            .awaitSingleOrNull()
            ?: chatMessageRepository.countByRoomId(roomId)
                .flatMap { count ->
                    messageCacheService.cacheMessageCount(roomId, count)
                        .thenReturn(count)
                }
                .awaitSingleOrNull() ?: 0L
    }

    suspend fun searchMessages(
        roomId: Long?,
        keyword: String?,
        userId: Long?,
        messageType: String?,
        startDate: String?,
        endDate: String?,
        page: Int,
        size: Int
    ): PagedApiResponse<MessageDto> = coroutineScope {
        val messageTypeEnum = messageType?.let { MessageType.valueOf(it.uppercase()) }
        val startDateTime = startDate?.let { java.time.ZonedDateTime.parse(it).withZoneSameInstant(java.time.ZoneOffset.UTC).toLocalDateTime() }
        val endDateTime = endDate?.let { java.time.ZonedDateTime.parse(it).withZoneSameInstant(java.time.ZoneOffset.UTC).toLocalDateTime() }

        val messagesDeferred = async {
            chatMessageRepository.searchMessages(
                roomId = roomId,
                keyword = keyword,
                userId = userId,
                messageType = messageTypeEnum,
                startDate = startDateTime,
                endDate = endDateTime,
                page = page,
                size = size
            ).map { it.toDto(keyword) }.collectList().awaitSingleOrNull() ?: emptyList()
        }

        val totalCountDeferred = async {
            chatMessageRepository.countSearchResults(
                roomId = roomId,
                keyword = keyword,
                userId = userId,
                messageType = messageTypeEnum,
                startDate = startDateTime,
                endDate = endDateTime
            ).awaitSingleOrNull() ?: 0L
        }

        val messages = messagesDeferred.await()
        val totalResults = totalCountDeferred.await()
        
        createPagedApiResponse(messages, page, size, totalResults)
    }

    /**
     * 새 메시지 저장 후 캐시 업데이트
     */
    suspend fun saveMessageAndUpdateCache(message: ChatMessage): ChatMessage {
        // 실제 저장은 도메인 서비스에서 처리되므로 여기서는 캐시 업데이트만
        messageCacheService.addNewMessageToCache(message.roomId, message).awaitSingleOrNull()
        messageCacheService.incrementMessageCount(message.roomId).awaitSingleOrNull()
        logger.debug("Updated cache for new message: roomId={}, messageId={}", message.roomId, message.id)
        return message
    }

    /**
     * 캐시 무효화
     */
    suspend fun invalidateRoomCache(roomId: Long) {
        messageCacheService.invalidateRoomCache(roomId).awaitSingleOrNull()
        logger.debug("Invalidated cache for room: {}", roomId)
    }

    /**
     * 캐시 통계 조회
     */
    suspend fun getCacheStats(roomId: Long): MessageCacheService.CacheStats? {
        return messageCacheService.getCacheStats(roomId).awaitSingleOrNull()
    }

    private suspend fun ChatMessage.toDtoWithUser(searchKeyword: String? = null): MessageDto {
        val userNickname = userRepository.findById(this.userId).awaitSingleOrNull()?.nickname ?: "Unknown User"
        return MessageDto(
            id = this.id,
            roomId = this.roomId.toString(),
            userId = this.userId,
            userNickname = userNickname,
            content = this.content,
            // CRITICAL FIX: LocalDateTime stored in DB should be interpreted as system timezone (KST)
            // then converted to UTC timestamp for consistent client handling
            timestamp = this.timestamp.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            highlightedContent = searchHighlighter.highlightKeyword(this.content, searchKeyword),
            messageType = this.messageType.name
        )
    }
    
    private fun ChatMessage.toDto(searchKeyword: String? = null): MessageDto {
        return MessageDto(
            id = this.id,
            roomId = this.roomId.toString(),
            userId = this.userId,
            userNickname = null, // JavaScript에서 사용자 정보를 조회하도록 수정
            content = this.content,
            // CRITICAL FIX: LocalDateTime stored in DB should be interpreted as system timezone (KST)
            // then converted to UTC timestamp for consistent frontend handling
            timestamp = this.timestamp.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            highlightedContent = searchHighlighter.highlightKeyword(this.content, searchKeyword),
            messageType = this.messageType.name
        )
    }

    private fun <T> createPagedApiResponse(
        content: List<T>,
        page: Int,
        size: Int,
        totalElements: Long
    ): PagedApiResponse<T> {
        val totalPages = if (size > 0) (totalElements + size - 1) / size else 0
        val paginationInfo = PaginationInfo(
            page = page,
            size = size,
            totalElements = totalElements,
            totalPages = totalPages.toInt(),
            hasNext = page < totalPages - 1,
            hasPrevious = page > 0
        )
        return PagedApiResponse.success(content, paginationInfo)
    }

}
