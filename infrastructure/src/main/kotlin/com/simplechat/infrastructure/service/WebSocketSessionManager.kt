package com.simplechat.infrastructure.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.reactive.socket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap

@Service
class WebSocketSessionManager {

    private val log = LoggerFactory.getLogger(javaClass)

    private val sessions = ConcurrentHashMap<String, WebSocketSession>()
    private val chatRoomSessions = ConcurrentHashMap<String, MutableSet<String>>()
    private val sessionToChatRoom = ConcurrentHashMap<String, String>()

    fun addSession(chatRoomId: String, session: WebSocketSession) {
        val sessionId = session.id
        sessions[sessionId] = session
        sessionToChatRoom[sessionId] = chatRoomId
        chatRoomSessions.computeIfAbsent(chatRoomId) { ConcurrentHashMap.newKeySet() }.add(sessionId)
        log.info("Session added: {} to chat room: {}", sessionId, chatRoomId)
    }

    fun removeSession(sessionId: String) {
        sessions.remove(sessionId)
        val chatRoomId = sessionToChatRoom.remove(sessionId)
        if (chatRoomId != null) {
            chatRoomSessions[chatRoomId]?.remove(sessionId)
            if (chatRoomSessions[chatRoomId]?.isEmpty() == true) {
                chatRoomSessions.remove(chatRoomId)
            }
            log.info("Session removed: {} from chat room: {}", sessionId, chatRoomId)
        } else {
            log.info("Session removed: {}, no chat room mapping found.", sessionId)
        }
    }

    fun getSessionsByChatRoom(chatRoomId: String): List<WebSocketSession> {
        return chatRoomSessions[chatRoomId]?.mapNotNull { sessions[it] } ?: emptyList()
    }

    @Scheduled(fixedRate = 60000) // Run every 60 seconds
    fun cleanupClosedSessions() {
        log.debug("Running scheduled cleanup of closed WebSocket sessions.")
        val closedSessionIds = sessions.filter { !it.value.isOpen }.keys
        if (closedSessionIds.isNotEmpty()) {
            log.info("Found {} closed sessions to clean up.", closedSessionIds.size)
            closedSessionIds.forEach { sessionId ->
                removeSession(sessionId)
            }
        }
    }
}
