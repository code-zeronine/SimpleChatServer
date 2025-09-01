package com.simplechat.infrastructure.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.simplechat.domain.message.*
import com.simplechat.domain.message.WebSocketMessage
import com.simplechat.domain.service.ChatMessageDomainService
import com.simplechat.domain.service.MessageBrokerDomainService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono
import com.simplechat.domain.entity.ChatMessage as ChatMessageEntity

@Component
class WebSocketMessageHandler(
    private val messageBrokerService: MessageBrokerDomainService,
    private val chatMessageService: ChatMessageDomainService,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun handleSession(session: WebSocketSession, chatRoomId: String, username: String, userId: Long): Mono<Void> {
        val receiveMono = session.receive()
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
                            return@flatMap chatMessageService.saveMessage(chatMessageEntity)
                                .then(Mono.fromRunnable { messageBrokerService.broadcast(chatRoomId, chatMessage) })
                        }
                        WebSocketMessageType.TYPING -> {
                            // Just broadcast, no DB save
                            messageBrokerService.broadcast(chatRoomId, messageDto)
                            return@flatMap Mono.empty<Void>()
                        }
                        else -> {
                            log.warn("Unhandled message type: {}", messageDto.type)
                            return@flatMap Mono.empty<Void>()
                        }
                    }
                } catch (e: Exception) {
                    log.error("Error processing WebSocket message: {}", e.message, e)
                    return@flatMap Mono.empty<Void>()
                }
            }.then()

        return receiveMono
    }
}
