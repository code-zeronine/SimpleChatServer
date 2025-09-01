package com.simplechat.service

import com.simplechat.domain.entity.ChatMessage
import reactor.core.publisher.Mono

/**
 * 채팅 메시지 관련 비즈니스 로직을 처리하는 서비스 인터페이스
 */
interface ChatMessageService {

    /**
     * 채팅 메시지를 저장합니다.
     *
     * @param message 저장할 채팅 메시지
     * @return 저장 작업이 완료되었음을 나타내는 Mono<Void>
     */
    fun saveMessage(message: ChatMessage): Mono<Void>
}
