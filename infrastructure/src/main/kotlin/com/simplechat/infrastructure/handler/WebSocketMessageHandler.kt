package com.simplechat.infrastructure.handler

import com.fasterxml.jackson.databind.ObjectMapper
import com.simplechat.dto.ChatMessage as ChatMessageDto
import com.simplechat.dto.WebSocketActionType
import com.simplechat.dto.WebSocketMessage
import com.simplechat.service.ChatMessageService
import com.simplechat.service.MessageBroker
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono
import com.simplechat.domain.entity.ChatMessage as ChatMessageEntity

@Component
class WebSocketMessageHandler(
    private val messageBroker: MessageBroker,
    private val chatMessageService: ChatMessageService, // Assuming an implementation will be provided
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
                        WebSocketActionType.CHAT -> {
                            val chatMessageDto = messageDto as ChatMessageDto
                            // DTO -> Domain Entity Mapping
                            val chatMessageEntity = ChatMessageEntity(
                                roomId = chatRoomId.toLong(), // Assuming chatRoomId can be converted to Long
                                userId = userId,
                                content = chatMessageDto.content
                            )
                            // Save to DB then broadcast
                            return@flatMap chatMessageService.saveMessage(chatMessageEntity)
                                .then(Mono.fromRunnable { messageBroker.broadcast(chatRoomId, chatMessageDto) })
                        }
                        WebSocketActionType.TYPING -> {
                            // Just broadcast, no DB save
                            messageBroker.broadcast(chatRoomId, messageDto)
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
