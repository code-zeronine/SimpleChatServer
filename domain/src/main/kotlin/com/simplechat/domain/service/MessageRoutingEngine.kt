package com.simplechat.domain.service

import com.simplechat.domain.message.MessageTarget
import com.simplechat.domain.message.WebSocketMessage
import reactor.core.publisher.Mono

/**
 * 고급 메시지 라우팅 및 브로드캐스팅 엔진
 */
interface MessageRoutingEngine {
    
    /**
     * 메시지 타입에 따른 라우팅 처리
     */
    fun routeMessage(message: WebSocketMessage, sourceSessionId: String): Mono<Void>
    
    /**
     * 채팅방 내 모든 사용자에게 브로드캐스트
     */
    fun broadcastToRoom(chatRoomId: String, message: WebSocketMessage, excludeSessionId: String? = null): Mono<Void>
    
    /**
     * 특정 사용자에게 직접 메시지 전송
     */
    fun sendToUser(userId: Long, message: WebSocketMessage): Mono<Void>
    
    /**
     * 특정 사용자 목록에게 메시지 전송
     */
    fun sendToUsers(userIds: Set<Long>, message: WebSocketMessage): Mono<Void>
    
    /**
     * 특정 세션에게 직접 메시지 전송
     */
    fun sendToSession(sessionId: String, message: WebSocketMessage): Mono<Void>
    
    /**
     * 전역 브로드캐스트 (모든 연결된 사용자에게)
     */
    fun broadcastGlobally(message: WebSocketMessage): Mono<Void>
    
    /**
     * 메시지 전송 실패 시 재시도
     */
    fun retryFailedMessage(message: WebSocketMessage, target: MessageTarget, maxRetries: Int = 3): Mono<Void>
}


/**
 * 메시지 라우팅 전략
 */
enum class RoutingStrategy {
    BROADCAST_TO_ROOM,      // 채팅방 내 모든 사용자
    SEND_TO_USER,           // 특정 사용자
    SEND_TO_USERS,          // 여러 사용자
    SEND_TO_SESSION,        // 특정 세션
    BROADCAST_GLOBALLY,     // 전역 브로드캐스트
    CUSTOM                  // 커스텀 라우팅
}

/**
 * 메시지 라우팅 결과
 */
data class RoutingResult(
    val success: Boolean,
    val deliveredCount: Int,
    val failedCount: Int,
    val errors: List<String> = emptyList()
)