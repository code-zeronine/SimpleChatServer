package com.simplechat.infrastructure.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.simplechat.domain.exception.websocket.WebSocketErrorCode
import com.simplechat.domain.exception.websocket.WebSocketException
import com.simplechat.domain.message.websocket.ErrorWebSocketMessage
import com.simplechat.domain.message.websocket.WebSocketMessageType
import com.simplechat.infrastructure.monitoring.ErrorSeverity
import com.simplechat.infrastructure.monitoring.WebSocketErrorMetrics
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * WebSocket 에러 처리를 담당하는 핸들러 클래스
 * 
 * 이 클래스는 WebSocket 통신 중 발생하는 모든 종류의 에러를 처리하고,
 * 클라이언트에게 적절한 에러 메시지를 전송하며, 에러 메트릭스를 수집합니다.
 */
@Component
class WebSocketErrorHandler(
    private val objectMapper: ObjectMapper,
    private val errorMetrics: WebSocketErrorMetrics
) {
    
    private val logger = LoggerFactory.getLogger(WebSocketErrorHandler::class.java)
    
    /**
     * WebSocket 예외를 처리하고 클라이언트에게 에러 메시지를 전송합니다.
     * 
     * @param session WebSocket 세션
     * @param exception 발생한 예외
     * @param originalMessage 원본 메시지 (있는 경우)
     * @return 에러 처리 결과 Mono
     */
    fun handleException(
        session: WebSocketSession,
        exception: Throwable,
        originalMessage: String? = null
    ): Mono<Void> {
        val errorCode = when (exception) {
            is WebSocketException -> exception.errorCode
            is IllegalArgumentException -> WebSocketErrorCode.WS_MESSAGE_VALIDATION_FAILED
            is com.fasterxml.jackson.core.JsonProcessingException -> WebSocketErrorCode.WS_MESSAGE_PARSING_FAILED
            is SecurityException -> WebSocketErrorCode.WS_AUTHENTICATION_FAILED
            else -> WebSocketErrorCode.WS_INTERNAL_ERROR
        }
        
        return handleError(session, errorCode, exception.message, originalMessage, exception)
    }
    
    /**
     * 지정된 에러 코드로 에러를 처리합니다.
     * 
     * @param session WebSocket 세션
     * @param errorCode WebSocket 에러 코드
     * @param customMessage 사용자 정의 메시지 (선택사항)
     * @param originalMessage 원본 메시지 (선택사항)
     * @param cause 원인 예외 (선택사항)
     * @return 에러 처리 결과 Mono
     */
    fun handleError(
        session: WebSocketSession,
        errorCode: WebSocketErrorCode,
        customMessage: String? = null,
        originalMessage: String? = null,
        cause: Throwable? = null
    ): Mono<Void> {
        // 에러 심각도 결정
        val severity = determineErrorSeverity(errorCode, cause)
        
        // 메트릭스에 에러 기록
        errorMetrics.recordError(session.id, errorCode, severity)
        
        val errorMessage = createErrorMessage(
            sessionId = session.id,
            errorCode = errorCode,
            customMessage = customMessage,
            originalMessage = originalMessage,
            cause = cause
        )
        
        // 로깅
        logError(session, errorCode, errorMessage, cause)
        
        // 알람 상태 확인
        checkAndTriggerAlarm(session)
        
        // 클라이언트에게 에러 메시지 전송
        return sendErrorMessage(session, errorMessage)
            .onErrorResume { sendError ->
                logger.error("Failed to send error message to client: ${sendError.message}", sendError)
                // 에러 메시지 전송에 실패해도 연결은 유지
                Mono.empty()
            }
    }
    
    /**
     * 연결 에러를 처리합니다.
     */
    fun handleConnectionError(
        session: WebSocketSession,
        reason: String? = null,
        shouldClose: Boolean = true
    ): Mono<Void> {
        val errorCode = WebSocketErrorCode.WS_CONNECTION_FAILED
        val details = reason?.let { mapOf("reason" to it) }
        
        val errorMessage = ErrorWebSocketMessage(
            type = WebSocketMessageType.ERROR,
            messageId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            sessionId = session.id,
            errorCode = errorCode.code,
            errorMessage = errorCode.message,
            details = details
        )
        
        logger.warn("Connection error for session {}: {}", session.id, reason ?: "Unknown reason")
        
        return if (shouldClose) {
            sendErrorMessage(session, errorMessage)
                .then(session.close())
        } else {
            sendErrorMessage(session, errorMessage)
        }
    }
    
    /**
     * 인증 에러를 처리합니다.
     */
    fun handleAuthenticationError(
        session: WebSocketSession,
        reason: String? = null
    ): Mono<Void> {
        val errorCode = WebSocketErrorCode.WS_AUTHENTICATION_FAILED
        logger.warn("Authentication failed for session {}: {}", session.id, reason ?: "Unknown reason")
        
        return handleError(session, errorCode, reason)
            .then(session.close())
    }
    
    /**
     * 메시지 파싱 에러를 처리합니다.
     */
    fun handleMessageParsingError(
        session: WebSocketSession,
        originalMessage: String,
        cause: Throwable? = null
    ): Mono<Void> {
        val errorCode = WebSocketErrorCode.WS_MESSAGE_PARSING_FAILED
        logger.warn("Message parsing failed for session {}: {}", session.id, cause?.message ?: "Unknown error")
        
        return handleError(session, errorCode, cause?.message, originalMessage, cause)
    }
    
    /**
     * 채팅방 접근 에러를 처리합니다.
     */
    fun handleChatRoomAccessError(
        session: WebSocketSession,
        chatRoomId: String,
        reason: String? = null
    ): Mono<Void> {
        val errorCode = WebSocketErrorCode.WS_CHATROOM_ACCESS_DENIED
        val details = mapOf(
            "chatRoomId" to chatRoomId,
            "reason" to (reason ?: "Access denied")
        )
        
        val errorMessage = ErrorWebSocketMessage(
            type = WebSocketMessageType.ERROR,
            messageId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            sessionId = session.id,
            errorCode = errorCode.code,
            errorMessage = errorCode.message,
            details = details
        )
        
        logger.warn("Chat room access denied for session {} to room {}: {}", 
            session.id, chatRoomId, reason ?: "Unknown reason")
        
        return sendErrorMessage(session, errorMessage)
    }
    
    /**
     * 속도 제한 에러를 처리합니다.
     */
    fun handleRateLimitError(
        session: WebSocketSession,
        limitType: String,
        retryAfterSeconds: Long? = null
    ): Mono<Void> {
        val errorCode = WebSocketErrorCode.WS_RATE_LIMIT_EXCEEDED
        val details = mutableMapOf<String, Any>()
        details["limitType"] = limitType
        retryAfterSeconds?.let { details["retryAfterSeconds"] = it }
        
        val errorMessage = ErrorWebSocketMessage(
            type = WebSocketMessageType.ERROR,
            messageId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            sessionId = session.id,
            errorCode = errorCode.code,
            errorMessage = errorCode.message,
            details = details
        )
        
        logger.warn("Rate limit exceeded for session {} ({}): retry after {} seconds", 
            session.id, limitType, retryAfterSeconds ?: "unknown")
        
        return sendErrorMessage(session, errorMessage)
    }
    
    /**
     * ErrorWebSocketMessage 객체를 생성합니다.
     */
    private fun createErrorMessage(
        sessionId: String,
        errorCode: WebSocketErrorCode,
        customMessage: String? = null,
        originalMessage: String? = null,
        cause: Throwable? = null
    ): ErrorWebSocketMessage {
        val details = mutableMapOf<String, Any>()
        cause?.let { 
            details["exceptionType"] = it.javaClass.simpleName
            it.message?.let { msg -> details["exceptionMessage"] = msg }
        }
        
        return ErrorWebSocketMessage(
            type = WebSocketMessageType.ERROR,
            messageId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            sessionId = sessionId,
            errorCode = errorCode.code,
            errorMessage = customMessage ?: errorCode.message,
            originalMessage = originalMessage,
            details = details.ifEmpty { null }
        )
    }
    
    /**
     * 클라이언트에게 에러 메시지를 전송합니다.
     */
    private fun sendErrorMessage(
        session: WebSocketSession,
        errorMessage: ErrorWebSocketMessage
    ): Mono<Void> {
        return try {
            val jsonMessage = objectMapper.writeValueAsString(errorMessage)
            val webSocketMessage = session.textMessage(jsonMessage)
            
            session.send(Mono.just(webSocketMessage))
                .doOnSuccess {
                    logger.debug("Error message sent to session {}: {}", session.id, errorMessage.errorCode)
                }
                .doOnError { error ->
                    logger.error("Failed to send error message to session {}: {}", 
                        session.id, error.message, error)
                }
        } catch (e: Exception) {
            logger.error("Failed to serialize error message for session {}: {}", 
                session.id, e.message, e)
            Mono.error(e)
        }
    }
    
    /**
     * 에러를 로깅합니다.
     */
    private fun logError(
        session: WebSocketSession,
        errorCode: WebSocketErrorCode,
        errorMessage: ErrorWebSocketMessage,
        cause: Throwable?
    ) {
        when {
            cause != null -> {
                logger.error("WebSocket error [{}] for session {}: {} - {}", 
                    errorCode.code, session.id, errorMessage.errorMessage, cause.message, cause)
            }
            errorCode.code.startsWith("WS-1") || errorCode.code.startsWith("WS-2") -> {
                // 인증/메시지 관련 에러는 WARN 레벨
                logger.warn("WebSocket error [{}] for session {}: {}", 
                    errorCode.code, session.id, errorMessage.errorMessage)
            }
            errorCode.code.startsWith("WS-4") -> {
                // 속도 제한은 INFO 레벨
                logger.info("WebSocket rate limit [{}] for session {}: {}", 
                    errorCode.code, session.id, errorMessage.errorMessage)
            }
            else -> {
                // 기타 에러는 ERROR 레벨
                logger.error("WebSocket error [{}] for session {}: {}", 
                    errorCode.code, session.id, errorMessage.errorMessage)
            }
        }
    }
    
    /**
     * 세션이 열려있는지 확인합니다.
     */
    fun isSessionOpen(session: WebSocketSession): Boolean {
        return session.isOpen
    }
    
    /**
     * 안전하게 세션을 닫습니다.
     */
    fun closeSessionSafely(session: WebSocketSession, reason: String? = null): Mono<Void> {
        // 세션 정리 (메트릭스에서 제거)
        errorMetrics.cleanupSession(session.id)
        
        return if (session.isOpen) {
            logger.info("Closing WebSocket session {}: {}", session.id, reason ?: "No reason provided")
            session.close()
                .doOnError { error ->
                    logger.warn("Error while closing session {}: {}", session.id, error.message)
                }
                .onErrorResume { Mono.empty() }
        } else {
            logger.debug("Session {} is already closed", session.id)
            Mono.empty()
        }
    }
    
    /**
     * 에러 심각도를 결정합니다.
     */
    private fun determineErrorSeverity(errorCode: WebSocketErrorCode, cause: Throwable?): ErrorSeverity {
        return when {
            // 시스템 레벨 에러들
            errorCode.code.startsWith("WS-6") -> ErrorSeverity.CRITICAL
            
            // 연결/인증 관련 에러들
            errorCode.code.startsWith("WS-1") -> when (errorCode) {
                WebSocketErrorCode.WS_CONNECTION_LIMIT_EXCEEDED -> ErrorSeverity.WARNING
                WebSocketErrorCode.WS_CONNECTION_TIMEOUT -> ErrorSeverity.WARNING
                else -> ErrorSeverity.ERROR
            }
            
            // 메시지 처리 에러들
            errorCode.code.startsWith("WS-2") -> when (errorCode) {
                WebSocketErrorCode.WS_MESSAGE_TOO_LARGE -> ErrorSeverity.WARNING
                WebSocketErrorCode.WS_MESSAGE_TYPE_UNSUPPORTED -> ErrorSeverity.INFO
                else -> ErrorSeverity.ERROR
            }
            
            // 채팅방 관련 에러들
            errorCode.code.startsWith("WS-3") -> ErrorSeverity.WARNING
            
            // 속도 제한 에러들
            errorCode.code.startsWith("WS-4") -> ErrorSeverity.INFO
            
            // 세션 관리 에러들
            errorCode.code.startsWith("WS-5") -> ErrorSeverity.WARNING
            
            else -> {
                // 원인 예외의 타입으로 심각도 결정
                when (cause) {
                    is SecurityException -> ErrorSeverity.CRITICAL
                    is IllegalStateException -> ErrorSeverity.ERROR
                    is IllegalArgumentException -> ErrorSeverity.WARNING
                    else -> ErrorSeverity.ERROR
                }
            }
        }
    }
    
    /**
     * 알람 상태를 확인하고 필요시 알람을 발생시킵니다.
     */
    private fun checkAndTriggerAlarm(session: WebSocketSession) {
        val alarmStatus = errorMetrics.shouldTriggerAlarm()
        when (alarmStatus) {
            com.simplechat.infrastructure.monitoring.AlarmStatus.CRITICAL -> {
                logger.error("CRITICAL: WebSocket error rate is critically high! Immediate attention required.")
                // 여기서 알람 시스템에 통지를 보낼 수 있음 (예: Slack, Email 등)
            }
            com.simplechat.infrastructure.monitoring.AlarmStatus.WARNING -> {
                logger.warn("WARNING: WebSocket error rate is elevated. Monitor closely.")
            }
            com.simplechat.infrastructure.monitoring.AlarmStatus.NORMAL -> {
                // 정상 상태, 로그 불필요
            }
        }
        
        // 특정 세션의 연속 에러 확인
        val sessionErrorInfo = errorMetrics.getSessionErrorInfo(session.id)
        if (sessionErrorInfo != null && sessionErrorInfo.consecutiveErrors >= 10) {
            logger.warn("Session {} has {} consecutive errors - consider disconnecting", 
                session.id, sessionErrorInfo.consecutiveErrors)
        }
    }
    
    /**
     * 에러 통계를 조회합니다.
     */
    fun getErrorStatistics() = errorMetrics.getErrorStatistics()
    
    /**
     * 특정 세션의 에러 정보를 조회합니다.
     */
    fun getSessionErrorInfo(sessionId: String) = errorMetrics.getSessionErrorInfo(sessionId)
}