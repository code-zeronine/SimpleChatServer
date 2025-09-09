package com.simplechat.domain.service

import com.simplechat.domain.message.websocket.WebSocketMessage

/**
 * 메시지 브로커 도메인 서비스 인터페이스
 * 
 * Infrastructure 계층에서 사용할 수 있는 Domain 서비스 인터페이스
 */
interface MessageBrokerDomainService {
    
    /**
     * 메시지를 브로드캐스트합니다.
     */
    fun broadcast(chatRoomId: String, message: WebSocketMessage)
    
    /**
     * 특정 사용자에게 메시지를 전송합니다.
     */
    fun sendToUser(userId: Long, message: WebSocketMessage)
}