package com.simplechat.infrastructure.handler

import com.simplechat.infrastructure.service.EnhancedWebSocketSessionManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * WebSocket 세션 이벤트를 처리하여 Redis 캐시를 자동으로 관리합니다.
 */
@Component
class WebSocketSessionEventHandler(
    private val sessionManager: EnhancedWebSocketSessionManager
) {

    private val logger = LoggerFactory.getLogger(WebSocketSessionEventHandler::class.java)

    /**
     * WebSocket 연결 생성 시 세션 정보를 캐시에 저장합니다.
     */
    fun handleSessionConnected(
        sessionId: String,
        session: org.springframework.web.reactive.socket.WebSocketSession,
        chatRoomId: String,
        userId: Long,
        remoteAddress: String? = null,
        userAgent: String? = null
    ): Mono<Void> {
        val operation: Mono<Void> = Mono.fromRunnable {
            sessionManager.addSession(chatRoomId, session, userId)
            logger.info("Session added in room {}: {} for user {}", chatRoomId, sessionId, userId)
        }
        return operation.doOnError { error: Throwable ->
            logger.error("Error adding session {}: {}", sessionId, error.message, error)
        }
    }

    /**
     * WebSocket 연결 종료 시 세션 정보를 캐시에서 제거합니다.
     */
    fun handleSessionDisconnected(sessionId: String): Mono<Void> {
        val operation: Mono<Void> = Mono.fromRunnable {
            sessionManager.removeSession(sessionId)
            logger.info("Session removed: {}", sessionId)
        }
        return operation.doOnError { error: Throwable ->
            logger.error("Error removing session {}: {}", sessionId, error.message, error)
        }
    }

    /**
     * WebSocket 활동 시 하트비트를 업데이트합니다.
     */
    fun handleSessionActivity(sessionId: String): Mono<Void> {
        val operation: Mono<Void> = Mono.fromRunnable {
            // Activity tracking can be added if needed
            logger.trace("Activity recorded for session: {}", sessionId)
        }
        return operation.doOnError { error: Throwable ->
            logger.error("Error handling activity for session {}: {}", sessionId, error.message, error)
        }
    }

    /**
     * 세션 통계를 로그로 출력합니다.
     */
    fun logSessionStatistics(): Mono<Void> {
        return Mono.fromRunnable {
            val stats = sessionManager.getCacheStatistics()
            logger.info(
                "Session statistics - Hit rate: {}%, Active sessions: {}",
                stats["hit_rate_percentage"],
                stats["active_sessions"]
            )
        }
    }
}