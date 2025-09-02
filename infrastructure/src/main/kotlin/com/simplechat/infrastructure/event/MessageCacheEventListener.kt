package com.simplechat.infrastructure.event

import com.simplechat.domain.event.MessageSavedEvent
import com.simplechat.infrastructure.service.MessageCacheService
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.scheduling.annotation.Async

/**
 * 메시지 캐시 이벤트 리스너
 * 
 * 메시지 저장 이벤트를 처리하여 캐시를 업데이트합니다.
 * 비동기로 처리하여 메인 처리 흐름에 영향을 주지 않습니다.
 */
@Component
class MessageCacheEventListener(
    private val messageCacheService: MessageCacheService
) {
    
    private val logger = LoggerFactory.getLogger(MessageCacheEventListener::class.java)

    /**
     * 메시지 저장 이벤트 처리
     */
    @Async
    @EventListener
    fun handleMessageSaved(event: MessageSavedEvent) {
        try {
            logger.debug("Processing message saved event: roomId={}, messageId={}", event.message.roomId, event.message.id)
            
            // 캐시 업데이트 (비동기)
            messageCacheService.addNewMessageToCache(event.message.roomId, event.message)
                .then(messageCacheService.incrementMessageCount(event.message.roomId))
                .doOnSuccess {
                    logger.debug("Cache updated for message: roomId={}, messageId={}", event.message.roomId, event.message.id)
                }
                .doOnError { error ->
                    logger.warn("Failed to update cache for message: roomId={}, messageId={}, error={}", 
                               event.message.roomId, event.message.id, error.message)
                }
                .subscribe()
                
        } catch (error: Exception) {
            logger.error("Error processing message saved event: roomId={}, messageId={}, error={}", 
                        event.message.roomId, event.message.id, error.message, error)
        }
    }
}