package com.simplechat.infrastructure.messaging.model

import com.simplechat.domain.message.MessageTarget
import com.simplechat.domain.message.websocket.WebSocketMessage
import java.time.Instant

/**
 * 실패한 메시지 정보
 */
data class FailedMessage(
    val id: String,
    val target: MessageTarget,
    val message: WebSocketMessage,
    val error: String,
    val failedAt: Instant,
    val retryCount: Int
)