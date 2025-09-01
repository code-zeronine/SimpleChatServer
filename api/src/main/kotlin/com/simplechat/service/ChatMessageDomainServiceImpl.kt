package com.simplechat.service

import com.simplechat.domain.entity.ChatMessage
import com.simplechat.domain.service.ChatMessageDomainService
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

/**
 * 채팅 메시지 도메인 서비스 구현체
 * 
 * Domain 인터페이스를 API 계층에서 구현
 */
@Service
class ChatMessageDomainServiceImpl : ChatMessageDomainService {
    
    override fun saveMessage(message: ChatMessage): Mono<Void> {
        // 실제 구현은 나중에 Repository를 통해 처리
        // 현재는 로그만 출력
        return Mono.fromRunnable {
            println("Saving chat message: ${message.content} from user ${message.userId} in room ${message.roomId}")
        }
    }
    
    override fun validateMessage(message: ChatMessage): Boolean {
        return message.isValid()
    }
}