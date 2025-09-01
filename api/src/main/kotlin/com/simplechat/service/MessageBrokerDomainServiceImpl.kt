package com.simplechat.service

import com.simplechat.domain.message.WebSocketMessage
import com.simplechat.domain.service.MessageBrokerDomainService
import org.springframework.stereotype.Service

/**
 * 메시지 브로커 도메인 서비스 구현체
 * 
 * Domain 인터페이스를 API 계층에서 구현
 * Infrastructure의 UnifiedRedisMessageService를 주입받아 위임
 */
@Service
class MessageBrokerDomainServiceImpl(
    // 실제로는 Infrastructure의 서비스를 주입받아야 하지만
    // 순환 의존성을 피하기 위해 Event 방식으로 구현 예정
) : MessageBrokerDomainService {
    
    override fun broadcast(chatRoomId: String, message: WebSocketMessage) {
        // ApplicationEventPublisher를 통해 이벤트 발행
        println("Broadcasting message to room $chatRoomId: ${message.type}")
    }
    
    override fun sendToUser(userId: Long, message: WebSocketMessage) {
        // ApplicationEventPublisher를 통해 이벤트 발행
        println("Sending message to user $userId: ${message.type}")
    }
}