package com.simplechat.service

import com.simplechat.dto.session.SessionDetail
import com.simplechat.dto.session.UserSessionInfo
import com.simplechat.infrastructure.session.WebSocketSessionManager
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 사용자 세션 무효화 및 중복 로그인 제어 서비스
 */
@Service
class SessionInvalidationService(
    private val webSocketSessionManager: WebSocketSessionManager
) {

    private val logger = LoggerFactory.getLogger(SessionInvalidationService::class.java)

    /**
     * 사용자의 모든 활성 세션을 무효화
     */
    suspend fun invalidateAllUserSessions(userId: Long): Int {
        logger.info("Invalidating all sessions for user: {}", userId)
        
        val activeSessions = webSocketSessionManager.getSessionsByUserId(userId)
        val sessionCount = activeSessions.size
        
        activeSessions.forEach { session ->
            try {
                // WebSocket 세션 강제 종료
                session.close().subscribe()
                logger.debug("Closed WebSocket session: {}", session.id)
            } catch (e: Exception) {
                logger.warn("Failed to close WebSocket session {}: {}", session.id, e.message)
            }
        }
        
        // 세션 매니저에서도 제거
        activeSessions.forEach { session ->
            webSocketSessionManager.removeSession(session.id)
        }
        
        logger.info("Invalidated {} sessions for user: {}", sessionCount, userId)
        return sessionCount
    }

    /**
     * 특정 세션을 제외하고 사용자의 다른 모든 세션을 무효화
     */
    suspend fun invalidateOtherUserSessions(userId: Long, excludeSessionId: String): Int {
        logger.info("Invalidating other sessions for user: {} (excluding: {})", userId, excludeSessionId)
        
        val activeSessions = webSocketSessionManager.getSessionsByUserId(userId)
            .filter { it.id != excludeSessionId }
        
        val sessionCount = activeSessions.size
        
        activeSessions.forEach { session ->
            try {
                // WebSocket 세션 강제 종료
                session.close().subscribe()
                logger.debug("Closed WebSocket session: {}", session.id)
            } catch (e: Exception) {
                logger.warn("Failed to close WebSocket session {}: {}", session.id, e.message)
            }
        }
        
        // 세션 매니저에서도 제거
        activeSessions.forEach { session ->
            webSocketSessionManager.removeSession(session.id)
        }
        
        logger.info("Invalidated {} other sessions for user: {}", sessionCount, userId)
        return sessionCount
    }

    /**
     * 사용자의 활성 세션 수 조회
     */
    fun getUserActiveSessionCount(userId: Long): Int {
        return webSocketSessionManager.getUserActiveSessionCount(userId)
    }

    /**
     * 특정 세션 무효화
     */
    suspend fun invalidateSession(sessionId: String): Boolean {
        logger.info("Invalidating session: {}", sessionId)
        
        val session = webSocketSessionManager.getSessionById(sessionId)
        return if (session != null) {
            try {
                session.close().subscribe()
                webSocketSessionManager.removeSession(sessionId)
                logger.info("Successfully invalidated session: {}", sessionId)
                true
            } catch (e: Exception) {
                logger.warn("Failed to invalidate session {}: {}", sessionId, e.message)
                false
            }
        } else {
            logger.warn("Session not found: {}", sessionId)
            false
        }
    }

    /**
     * 사용자의 세션 정보 조회
     */
    fun getUserSessionInfo(userId: Long): UserSessionInfo {
        val activeSessions = webSocketSessionManager.getSessionsByUserId(userId)
        
        val sessionDetails = activeSessions.map { session ->
            val metadata = webSocketSessionManager.getSessionMetadata(session.id)
            SessionDetail(
                sessionId = session.id,
                chatRoomId = webSocketSessionManager.getChatRoomBySessionId(session.id),
                connectedAt = metadata?.connectedAt,
                lastActivityAt = metadata?.lastActivityAt,
                messageCount = metadata?.messageCount ?: 0,
                isActive = session.isOpen
            )
        }
        
        return UserSessionInfo(
            userId = userId,
            totalSessions = sessionDetails.size,
            activeSessions = sessionDetails.count { it.isActive },
            sessions = sessionDetails
        )
    }
}

