package com.simplechat.infrastructure.handler

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono

/**
 * 채팅 WebSocket 핸들러
 * 
 * WebSocket 연결을 관리하고 메시지 처리 로직과 연동합니다.
 */
@Component
class ChatWebSocketHandler(
    private val messageHandler: WebSocketMessageHandler
) : WebSocketHandler {
    
    private val logger = LoggerFactory.getLogger(ChatWebSocketHandler::class.java)
    
    override fun handle(session: WebSocketSession): Mono<Void> {
        // URI에서 사용자 ID 추출 (예: /ws/chat/123)
        val userId = extractUserIdFromPath(session) ?: return session.close()
        
        logger.info("WebSocket connection established for user {} from {}", 
            userId, session.handshakeInfo.remoteAddress)
        
        val derivedChatRoomId = "room-${userId}"
        val derivedUsername = "user-${userId}"
        return messageHandler.handleSession(session, derivedChatRoomId, derivedUsername, userId)
            .doOnError { error ->
                logger.error("WebSocket session error for user {}: {}", userId, error.message, error)
            }
            .onErrorResume { error ->
                logger.error("Closing WebSocket session due to error for user {}: {}", 
                    userId, error.message)
                session.close()
            }
    }
    
    /**
     * WebSocket URI 경로에서 사용자 ID를 추출합니다.
     * 
     * @param session WebSocket 세션
     * @return 사용자 ID (추출 실패 시 null)
     */
    private fun extractUserIdFromPath(session: WebSocketSession): Long? {
        return try {
            val path = session.handshakeInfo.uri.path
            logger.debug("Extracting user ID from path: {}", path)
            
            // 경로가 /ws/chat/{userId} 형태인 경우
            val pathSegments = path.split("/")
            if (pathSegments.size >= 4 && pathSegments[1] == "ws" && pathSegments[2] == "chat") {
                val userId = pathSegments[3].toLongOrNull()
                if (userId != null) {
                    logger.debug("Extracted user ID: {}", userId)
                    return userId
                }
            }
            
            // 쿼리 파라미터에서 userId 추출 시도
            session.handshakeInfo.uri.query?.let { query ->
                val params = query.split("&")
                params.forEach { param ->
                    val keyValue = param.split("=")
                    if (keyValue.size == 2 && keyValue[0] == "userId") {
                        val userId = keyValue[1].toLongOrNull()
                        if (userId != null) {
                            logger.debug("Extracted user ID from query param: {}", userId)
                            return userId
                        }
                    }
                }
            }
            
            logger.warn("Unable to extract user ID from WebSocket path: {}", path)
            null
        } catch (e: Exception) {
            logger.error("Error extracting user ID from WebSocket path: {}", e.message, e)
            null
        }
    }
}