package com.simplechat.infrastructure.handler

import com.simplechat.domain.exception.WebSocketAuthenticationException
import com.simplechat.domain.exception.WebSocketConnectionException
import com.simplechat.domain.exception.WebSocketErrorCode
import com.simplechat.infrastructure.security.WebSocketAuthService
import com.simplechat.infrastructure.security.jwt.JwtUserDetails
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono

/**
 * 채팅 WebSocket 핸들러
 * 
 * WebSocket 연결을 관리하고 메시지 처리 로직과 연동합니다.
 * 통합된 에러 처리 시스템을 포함합니다.
 */
@Component
class ChatWebSocketHandler(
    private val messageHandler: WebSocketMessageHandler,
    private val errorHandler: WebSocketErrorHandler,
    private val authService: WebSocketAuthService
) : WebSocketHandler {
    
    private val logger = LoggerFactory.getLogger(ChatWebSocketHandler::class.java)
    
    override fun handle(session: WebSocketSession): Mono<Void> {
        return Mono.fromCallable { 
            logger.info("WebSocket connection attempt from {}", session.handshakeInfo.remoteAddress)
            session
        }
        .flatMap { validateConnection(it) }
        .flatMap { validatedSession ->
            // URI에서 사용자 ID 추출
            val userId = extractUserIdFromPath(validatedSession)
            if (userId == null) {
                return@flatMap errorHandler.handleAuthenticationError(
                    validatedSession, 
                    "사용자 ID를 추출할 수 없습니다"
                )
            }
            
            // 인증 확인 및 사용자 정보 추출
            Mono.defer { ReactiveSecurityContextHolder.getContext() }
                .map { it.authentication }
                .cast(UsernamePasswordAuthenticationToken::class.java)
                .map { it.principal }
                .cast(JwtUserDetails::class.java)
                .flatMap { userDetails ->
                    val authenticatedUserId = userDetails.id
                    logger.info("WebSocket connection established for user {} (ID: {}) from {}", 
                        userDetails.username, authenticatedUserId, session.handshakeInfo.remoteAddress)
                    
                    // URL에서 채팅방 ID 추출 (쿼리 파라미터에서)
                    val actualRoomId = extractRoomIdFromSession(validatedSession) ?: run {
                        logger.warn("No roomId found in WebSocket URL for user {}", authenticatedUserId)
                        return@flatMap errorHandler.handleAuthenticationError(
                            validatedSession, 
                            "채팅방 ID가 필요합니다"
                        )
                    }
                    
                    val derivedUsername = "user-${authenticatedUserId}"
                    
                    // 메시지 핸들링 시작
                    messageHandler.handleSession(validatedSession, actualRoomId, derivedUsername, authenticatedUserId)
                }
                .onErrorResume { error ->
                    logger.warn("Authentication or user details extraction failed: {}", error.message)
                    errorHandler.handleAuthenticationError(
                        validatedSession, 
                        "인증에 실패했거나 사용자 정보를 가져올 수 없습니다: ${error.message}"
                    )
                }
        }
        .doOnError { error ->
            logger.error("WebSocket connection error: {}", error.message, error)
        }
        .onErrorResume { error ->
            when (error) {
                is WebSocketConnectionException -> {
                    errorHandler.handleConnectionError(session, error.message, true)
                }
                is WebSocketAuthenticationException -> {
                    errorHandler.handleAuthenticationError(session, error.message)
                }
                else -> {
                    logger.error("Unexpected WebSocket error: {}", error.message, error)
                    errorHandler.handleException(session, error)
                        .then(errorHandler.closeSessionSafely(session, "Unexpected error"))
                }
            }
        }
    }
    
    /**
     * 연결 유효성을 검사합니다.
     */
    private fun validateConnection(session: WebSocketSession): Mono<WebSocketSession> {
        return Mono.fromCallable {
            // 기본적인 연결 검증
            if (!session.isOpen) {
                throw WebSocketConnectionException(
                    "세션이 이미 닫혀있습니다",
                    WebSocketErrorCode.WS_CONNECTION_CLOSED
                )
            }
            
            // 추가적인 연결 제한 검사는 AuthService에서 처리
            session
        }
    }
    
    /**
     * WebSocket 세션에서 채팅방 ID를 추출합니다.
     * 
     * @param session WebSocket 세션
     * @return 채팅방 ID (추출 실패 시 null)
     */
    private fun extractRoomIdFromSession(session: WebSocketSession): String? {
        return try {
            // 쿼리 파라미터에서 roomId 추출
            session.handshakeInfo.uri.query?.let { query ->
                val params = query.split("&")
                params.forEach { param ->
                    val keyValue = param.split("=")
                    if (keyValue.size == 2 && keyValue[0] == "roomId") {
                        logger.debug("Extracted roomId from query param: {}", keyValue[1])
                        return keyValue[1]
                    }
                }
            }
            
            logger.warn("Unable to extract roomId from WebSocket URL: {}", session.handshakeInfo.uri)
            null
        } catch (e: Exception) {
            logger.error("Error extracting roomId from WebSocket URL: {}", e.message, e)
            null
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