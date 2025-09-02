package com.simplechat.infrastructure.service

import com.simplechat.domain.entity.ChatMessage
import com.simplechat.domain.service.ChatMessageDomainService
import com.simplechat.infrastructure.entity.ChatMessageEntity
import com.simplechat.infrastructure.repository.ChatMessageMongoRepository
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.LocalDateTime

/**
 * 채팅 메시지 도메인 서비스 구현체
 * 
 * Domain 인터페이스를 Infrastructure 계층에서 구현
 * Infrastructure Repository를 통해 실제 저장 처리
 */
@Service
class ChatMessageService(
    private val chatMessageRepository: ChatMessageMongoRepository
) : ChatMessageDomainService {
    
    override fun saveMessage(message: ChatMessage): Mono<Void> {
        // Domain Entity를 Infrastructure Entity로 변환
        val entity = ChatMessageEntity(
            roomId = message.roomId,
            userId = message.userId,
            content = message.content,
            messageType = message.messageType,
            timestamp = LocalDateTime.now()
        )
        
        return chatMessageRepository.save(entity).then()
    }
    
    override fun validateMessage(message: ChatMessage): Boolean {
        return message.isValid()
    }
}