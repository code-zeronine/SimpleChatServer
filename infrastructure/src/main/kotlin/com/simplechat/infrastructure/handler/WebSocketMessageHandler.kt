package com.simplechat.infrastructure.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.simplechat.domain.entity.ChatMessage
import com.simplechat.domain.message.ChatWebSocketMessage
import com.simplechat.domain.message.JoinWebSocketMessage
import com.simplechat.domain.message.LeaveWebSocketMessage
import com.simplechat.domain.message.SystemWebSocketMessage
import com.simplechat.domain.message.TypingWebSocketMessage
import com.simplechat.domain.message.WebSocketMessage
import com.simplechat.domain.message.WebSocketMessageType
import com.simplechat.domain.service.ChatMessageDomainService
import com.simplechat.domain.service.MessageBrokerDomainService
import com.simplechat.infrastructure.service.RedisMessageBrokerService
import com.simplechat.infrastructure.service.WebSocketSessionManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.*

@Component
class WebSocketMessageHandler(
    private val messageBrokerService: MessageBrokerDomainService,
    private val redisMessageBrokerService: RedisMessageBrokerService,
    private val chatMessageService: ChatMessageDomainService,
    private val objectMapper: ObjectMapper,
    private val sessionManager: WebSocketSessionManager,
    private val errorHandler: WebSocketErrorHandler
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun handleSession(session: WebSocketSession, chatRoomId: String, username: String, userId: Long): Mono<Void> {
        // Add session to manager when connected
        sessionManager.addSession(chatRoomId, session, userId)
        log.info("Session added for user {} in room {}: {}", userId, chatRoomId, session.id)

        // Redis 채널 구독 및 메시지를 WebSocket으로 전달
        val subscriptionFlux = redisMessageBrokerService.subscribeToChatRoom(chatRoomId)
            .filter { message ->
                // 자신이 보낸 메시지는 제외 (메시지에 userId가 있는 경우)
                when (message) {
                    is ChatWebSocketMessage -> message.userId != userId
                    is JoinWebSocketMessage -> message.userId != userId
                    is LeaveWebSocketMessage -> message.userId != userId
                    is TypingWebSocketMessage -> message.userId != userId
                    else -> true // 시스템 메시지 등은 모두에게 전달
                }
            }
            .flatMap { message ->
                try {
                    // 세션이 열려있는지 확인
                    if (!session.isOpen) {
                        log.debug("Session {} is closed, skipping message send", session.id)
                        return@flatMap Mono.empty<Void>()
                    }
                    
                    val messageJson = objectMapper.writeValueAsString(message)
                    session.send(Mono.just(session.textMessage(messageJson)))
                        .doOnSuccess { log.debug("Broadcasted message to session {}: {}", session.id, message.type) }
                        .onErrorResume { error ->
                            log.debug("Failed to send message to session {}: {}", session.id, error.message)
                            // 연결이 닫힌 경우 세션 정리
                            if (error.message?.contains("Connection has been closed") == true) {
                                sessionManager.removeSession(session.id)
                            }
                            Mono.empty()
                        }
                } catch (e: Exception) {
                    log.error("Failed to serialize message for session {}: {}", session.id, e.message)
                    Mono.empty<Void>()
                }
            }
            .doOnError { error -> log.error("Redis subscription error for room {}: {}", chatRoomId, error.message) }
            .onErrorResume { Mono.empty<Void>().flux() }

        // 클라이언트로부터 오는 메시지 처리
        val receiveFlux = session.receive()
            .flatMap { webSocketMessage ->
                handleIncomingMessage(session, webSocketMessage, chatRoomId)
            }

        // 구독과 수신을 병렬로 처리
        return subscriptionFlux.mergeWith(receiveFlux)
            .doFinally { signalType -> // Handle session disconnection
                sessionManager.removeSession(session.id)
                redisMessageBrokerService.unsubscribeFromChatRoom(chatRoomId).subscribe()
                
                // 연결 해제 시 자동으로 퇴장 시스템 메시지 전송
                if (signalType != reactor.core.publisher.SignalType.ON_COMPLETE) {
                    try {
                        val leaveSystemMessage = SystemWebSocketMessage(
                            messageId = UUID.randomUUID().toString(),
                            timestamp = Instant.now(),
                            sessionId = session.id,
                            content = "$username 님이 퇴장했습니다."
                        )
                        messageBrokerService.broadcast(chatRoomId, leaveSystemMessage)
                        log.debug("Auto-sent leave message for user {} in room {}", userId, chatRoomId)
                    } catch (e: Exception) {
                        log.warn("Failed to send auto-leave message for user {} in room {}: {}", userId, chatRoomId, e.message)
                    }
                }
                
                log.info("Session removed for user {} in room {}: {} (Signal: {})", userId, chatRoomId, session.id, signalType)
            }
            .then() // Complete the Mono<Void>
    }
    
    /**
     * 수신된 WebSocket 메시지를 처리합니다.
     */
    private fun handleIncomingMessage(
        session: WebSocketSession,
        webSocketMessage: org.springframework.web.reactive.socket.WebSocketMessage,
        chatRoomId: String
    ): Mono<Void> {
        return Mono.fromCallable {
            // 메시지 크기 제한 검사 (예: 64KB)
            val messageText = webSocketMessage.payloadAsText
            if (messageText.length > 65536) {
                throw IllegalArgumentException("Message too large: ${messageText.length} bytes")
            }
            messageText
        }
        .flatMap { messageText ->
            parseAndProcessMessage(session, messageText, chatRoomId)
        }
        .onErrorResume { error ->
            when (error) {
                is IllegalArgumentException -> {
                    if (error.message?.contains("Message too large") == true) {
                        errorHandler.handleError(
                            session,
                            com.simplechat.domain.exception.WebSocketErrorCode.WS_MESSAGE_TOO_LARGE,
                            error.message
                        )
                    } else {
                        errorHandler.handleError(
                            session,
                            com.simplechat.domain.exception.WebSocketErrorCode.WS_MESSAGE_VALIDATION_FAILED,
                            error.message
                        )
                    }
                }
                is com.fasterxml.jackson.core.JsonProcessingException -> {
                    val originalMessage = webSocketMessage.payloadAsText
                    errorHandler.handleMessageParsingError(session, originalMessage, error)
                }
                else -> {
                    log.error("Unexpected error processing WebSocket message: {}", error.message, error)
                    errorHandler.handleException(session, error, webSocketMessage.payloadAsText)
                }
            }
        }
        .doOnError { _ ->
            // 세션 활동 업데이트 (에러 발생 시에도)
            sessionManager.updateSessionActivity(session.id)
        }
    }
    
    /**
     * 메시지를 파싱하고 처리합니다.
     */
    private fun parseAndProcessMessage(
        session: WebSocketSession,
        messageText: String,
        chatRoomId: String
    ): Mono<Void> {
        return Mono.fromCallable {
            try {
                objectMapper.readValue(messageText, WebSocketMessage::class.java)
            } catch (e: Exception) {
                log.warn("Failed to parse WebSocket message: {}", e.message)
                throw RuntimeException("Failed to parse WebSocket message", e)
            }
        }
        .flatMap { messageDto ->
            // 세션 활동 업데이트
            sessionManager.updateSessionActivity(session.id)
            
            // 메시지 타입별 처리
            processMessageByType(session, messageDto, chatRoomId)
        }
    }
    
    /**
     * 메시지 타입에 따라 처리합니다.
     */
    private fun processMessageByType(
        session: WebSocketSession,
        messageDto: WebSocketMessage,
        chatRoomId: String
    ): Mono<Void> {
        return try {
            when (messageDto.type) {
                WebSocketMessageType.CHAT -> {
                    val chatMessage = messageDto as ChatWebSocketMessage
                    handleChatMessage(session, chatMessage, chatRoomId)
                }
                WebSocketMessageType.JOIN -> {
                    val joinMessage = messageDto as JoinWebSocketMessage
                    handleJoinRoom(session, joinMessage)
                }
                WebSocketMessageType.LEAVE -> {
                    val leaveMessage = messageDto as LeaveWebSocketMessage
                    handleLeaveRoom(session, leaveMessage)
                }
                WebSocketMessageType.TYPING -> {
                    val typingMessage = messageDto as TypingWebSocketMessage
                    handleTypingMessage(session, typingMessage, chatRoomId)
                }
                WebSocketMessageType.HEARTBEAT -> {
                    handleHeartbeat(session)
                }
                else -> {
                    log.warn("Unhandled message type: {} from session {}", messageDto.type, session.id)
                    errorHandler.handleError(
                        session,
                        com.simplechat.domain.exception.WebSocketErrorCode.WS_MESSAGE_TYPE_UNSUPPORTED,
                        "지원되지 않는 메시지 타입: ${messageDto.type}"
                    )
                }
            }
        } catch (e: ClassCastException) {
            log.error("Message type mismatch for session {}: {}", session.id, e.message)
            errorHandler.handleError(
                session,
                com.simplechat.domain.exception.WebSocketErrorCode.WS_MESSAGE_MALFORMED,
                "메시지 형식이 올바르지 않습니다"
            )
        } catch (e: Exception) {
            log.error("Error processing message type {} for session {}: {}", 
                messageDto.type, session.id, e.message, e)
            errorHandler.handleException(session, e)
        }
    }
    
    /**
     * 채팅 메시지를 처리합니다.
     */
    private fun handleChatMessage(
        session: WebSocketSession,
        chatMessage: ChatWebSocketMessage,
        chatRoomId: String
    ): Mono<Void> {
        // DTO -> Domain Entity Mapping
        val domainChatMessage = ChatMessage(
            roomId = chatMessage.roomId,
            userId = chatMessage.userId,
            content = chatMessage.content
        )

        return chatMessageService.saveMessage(domainChatMessage)
            .then(Mono.defer {
                messageBrokerService.broadcast(chatRoomId, chatMessage)
                Mono.empty<Void>()
            })
            .onErrorResume { error ->
                log.error("Failed to save or broadcast chat message: {}", error.message, error)
                errorHandler.handleError(
                    session,
                    com.simplechat.domain.exception.WebSocketErrorCode.WS_MESSAGE_DELIVERY_FAILED,
                    "메시지 전송에 실패했습니다: ${error.message}"
                )
            }
    }
    
    /**
     * 타이핑 메시지를 처리합니다.
     */
    private fun handleTypingMessage(
        session: WebSocketSession,
        typingMessage: TypingWebSocketMessage,
        chatRoomId: String
    ): Mono<Void> {
        return Mono.defer {
            // Just broadcast, no DB save
            messageBrokerService.broadcast(chatRoomId, typingMessage)
            Mono.empty<Void>()
        }
        .onErrorResume { error ->
            log.error("Failed to broadcast typing message: {}", error.message)
            errorHandler.handleError(
                session,
                com.simplechat.domain.exception.WebSocketErrorCode.WS_MESSAGE_DELIVERY_FAILED,
                "타이핑 상태 전송에 실패했습니다"
            )
        }
    }
    
    /**
     * 하트비트 메시지를 처리합니다.
     */
    private fun handleHeartbeat(
        session: WebSocketSession
    ): Mono<Void> {
        log.debug("Heartbeat received from session: {}", session.id)
        sessionManager.handleHeartbeat(session.id)

        val pongMessage = mapOf(
            "type" to "pong",
            "timestamp" to Instant.now()
        )

        return Mono.fromCallable { objectMapper.writeValueAsString(pongMessage) }
            .flatMap { messageJson ->
                session.send(Mono.just(session.textMessage(messageJson)))
            }
            .doOnSuccess {
                log.debug("Pong message sent to session {}", session.id)
            }
            .doOnError { error ->
                log.warn("Failed to send pong message to session {}: {}", session.id, error.message)
            }
            .then()
    }

    // New methods for handling specific message types
    private fun handleJoinRoom(session: WebSocketSession, message: JoinWebSocketMessage): Mono<Void> {
        // Logic for handling join room message
        log.info("User {} ({}) joined room {}", message.userId, message.userNickname, message.roomId)
        
        val systemMessage = SystemWebSocketMessage(
            messageId = UUID.randomUUID().toString(),
            timestamp = Instant.now(),
            sessionId = session.id,
            content = "${message.userNickname ?: "사용자"} 님이 입장했습니다."
        )
        
        return Mono.defer {
            messageBrokerService.broadcast(message.roomId.toString(), systemMessage)
            Mono.empty<Void>()
        }
        .onErrorResume { error ->
            log.error("Failed to broadcast join message: {}", error.message)
            Mono.empty()
        }
    }

    private fun handleLeaveRoom(session: WebSocketSession, message: LeaveWebSocketMessage): Mono<Void> {
        // Logic for handling leave room message
        log.info("User {} ({}) left room {}", message.userId, message.userNickname, message.roomId)
        
        val systemMessage = SystemWebSocketMessage(
            messageId = UUID.randomUUID().toString(),
            timestamp = Instant.now(),
            sessionId = session.id,
            content = "${message.userNickname ?: "사용자"} 님이 퇴장했습니다."
        )
        
        return Mono.defer {
            messageBrokerService.broadcast(message.roomId.toString(), systemMessage)
            Mono.empty<Void>()
        }
        .onErrorResume { error ->
            log.error("Failed to broadcast leave message: {}", error.message)
            Mono.empty()
        }
    }
}