package com.simplechat.domain.event

import com.simplechat.domain.entity.ChatMessage
import java.time.Instant

/**
 * 메시지 저장 이벤트
 * 
 * 새로운 메시지가 저장되었을 때 발생하는 도메인 이벤트입니다.
 * 캐시 업데이트, 알림 전송 등의 부가적인 작업을 위해 사용됩니다.
 */
data class MessageSavedEvent(
    val message: ChatMessage,
    val occurredAt: Instant = Instant.now()
)