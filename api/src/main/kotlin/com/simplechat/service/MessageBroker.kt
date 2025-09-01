package com.simplechat.service

import com.simplechat.dto.WebSocketMessage

/**
 * 메시지 브로드캐스팅을 위한 인터페이스
 */
interface MessageBroker {
    
    /**
     * 특정 채팅방에 메시지를 브로드캐스팅합니다.
     *
     * @param chatRoomId 메시지를 보낼 채팅방 ID
     * @param message 브로드캐스팅할 WebSocket 메시지
     */
    fun broadcast(chatRoomId: String, message: WebSocketMessage)
}
