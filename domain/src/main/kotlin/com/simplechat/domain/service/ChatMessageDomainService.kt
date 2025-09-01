package com.simplechat.domain.service

import com.simplechat.domain.entity.ChatMessage
import reactor.core.publisher.Mono

/**
 * 채팅 메시지 도메인 서비스 인터페이스
 * 
 * Infrastructure 계층에서 사용할 수 있는 Domain 서비스 인터페이스
 */
interface ChatMessageDomainService {
    
    /**
     * 채팅 메시지를 저장합니다.
     */
    fun saveMessage(message: ChatMessage): Mono<Void>
    
    /**
     * 메시지 유효성을 검증합니다.
     */
    fun validateMessage(message: ChatMessage): Boolean
}