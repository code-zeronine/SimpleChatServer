package com.simplechat.infrastructure.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.simplechat.domain.message.ChatWebSocketMessage
import com.simplechat.domain.message.JoinWebSocketMessage
import com.simplechat.domain.message.LeaveWebSocketMessage
import com.simplechat.domain.message.SystemWebSocketMessage
import com.simplechat.domain.message.WebSocketMessage
import com.simplechat.domain.message.WebSocketMessageType
import com.simplechat.domain.service.ChatMessageDomainService
import com.simplechat.domain.service.MessageBrokerDomainService
import com.simplechat.infrastructure.service.WebSocketSessionManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.*
import com.simplechat.domain.entity.ChatMessage as ChatMessageEntity

@Component
class WebSocketMessageHandler(
    private val messageBrokerService: MessageBrokerDomainService,
    private val chatMessageService: ChatMessageDomainService,
    private val objectMapper: ObjectMapper,
    private val sessionManager: WebSocketSessionManager
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun handleSession(session: WebSocketSession, chatRoomId: String, username: String, userId: Long): Mono<Void> {
        // Add session to manager when connected
        sessionManager.addSession(chatRoomId, session, userId)
        log.info("Session added for user {} in room {}: {}", userId, chatRoomId, session.id)

        return session.receive()
            .flatMap { webSocketMessage ->
                try {
                    val messageText = webSocketMessage.payloadAsText
                    val messageDto = objectMapper.readValue(messageText, WebSocketMessage::class.java)

                    when (messageDto.type) {
                        WebSocketMessageType.CHAT -> {
                            val chatMessage = messageDto as ChatWebSocketMessage
                            // DTO -> Domain Entity Mapping
                            val chatMessageEntity = ChatMessageEntity(
                                roomId = chatMessage.roomId,
                                userId = chatMessage.userId,
                                content = chatMessage.content
                            )
                            // Save to DB then broadcast
                            chatMessageService.saveMessage(chatMessageEntity)
                                .then(Mono.fromRunnable { messageBrokerService.broadcast(chatRoomId, chatMessage) })
                        }
                        WebSocketMessageType.JOIN -> handleJoinRoom(session, messageDto as JoinWebSocketMessage) // Call new method
                        WebSocketMessageType.LEAVE -> handleLeaveRoom(session, messageDto as LeaveWebSocketMessage) // Call new method
                        WebSocketMessageType.TYPING -> {
                            // Just broadcast, no DB save
                            messageBrokerService.broadcast(chatRoomId, messageDto)
                            Mono.empty<Void>()
                        }
                        else -> {
                            log.warn("Unhandled message type: {}", messageDto.type)
                            Mono.empty<Void>()
                        }
                    }
                } catch (e: Exception) {
                    log.error("Error processing WebSocket message: {}", e.message, e)
                    Mono.empty<Void>() // Continue processing other messages, don't terminate stream
                }
            }
            .doFinally { signalType -> // Handle session disconnection
                sessionManager.removeSession(session.id)
                log.info("Session removed for user {} in room {}: {} (Signal: {})", userId, chatRoomId, session.id, signalType)
            }
            .then() // Complete the Mono<Void>
    }

    // New methods for handling specific message types
    private fun handleJoinRoom(session: WebSocketSession, message: JoinWebSocketMessage): Mono<Void> {
        // Logic for handling join room message
        // For now, just log and broadcast a system message
        log.info("User {} joined room {}", message.userId, message.roomId)
        val systemMessage = SystemWebSocketMessage(
            messageId = UUID.randomUUID().toString(),
            timestamp = Instant.now(),
            sessionId = session.id,
            content = "${message.userNickname} 님이 입장했습니다."
        )
        val broadcastMono: Mono<Void> = Mono.just(Unit)
            .doOnNext { messageBrokerService.broadcast(message.roomId.toString(), systemMessage) }
            .then()
        return broadcastMono
    }

    private fun handleLeaveRoom(session: WebSocketSession, message: LeaveWebSocketMessage): Mono<Void> {
        // Logic for handling leave room message
        // For now, just log and broadcast a system message
        log.info("User {} left room {}", message.userId, message.roomId)
        val systemMessage = SystemWebSocketMessage(
            messageId = UUID.randomUUID().toString(),
            timestamp = Instant.now(),
            sessionId = session.id,
            content = "${message.userNickname} 님이 퇴장했습니다."
        )
        val broadcastMono: Mono<Void> = Mono.just(Unit)
            .doOnNext { messageBrokerService.broadcast(message.roomId.toString(), systemMessage) }
            .then()
        return broadcastMono
    }
}