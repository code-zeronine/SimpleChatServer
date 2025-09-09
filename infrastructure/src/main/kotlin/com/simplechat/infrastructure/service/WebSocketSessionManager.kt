package com.simplechat.infrastructure.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.reactive.socket.WebSocketSession
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 통합된 WebSocket 세션 관리자
 * 
 * 기존의 여러 세션 관리 서비스들을 하나로 통합:
 * - WebSocketSessionManager
 * - EnhancedWebSocketSessionManager  
 * - WebSocketSessionCacheService
 * 
 * 사용자별, 채팅방별 세션 관리, 캐싱, 고급 라우팅 기능 제공
 */
@Service
class WebSocketSessionManager {

    private val logger = LoggerFactory.getLogger(WebSocketSessionManager::class.java)

    // 세션 저장소 (캐싱 기능 통합)
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()
    
    // 세션별 메타데이터 (확장된 캐시 정보 포함)
    private val sessionMetadata = ConcurrentHashMap<String, SessionMetadata>()
    
    // 채팅방별 세션 그룹
    private val chatRoomSessions = ConcurrentHashMap<String, MutableSet<String>>()
    
    // 사용자별 세션 그룹
    private val userSessions = ConcurrentHashMap<Long, MutableSet<String>>()
    
    // 세션-채팅방 매핑
    private val sessionToChatRoom = ConcurrentHashMap<String, String>()
    
    // 세션-사용자 매핑
    private val sessionToUser = ConcurrentHashMap<String, Long>()
    
    // 통계 및 모니터링 (캐시 히트율 등)
    private val totalSessionsCreated = AtomicLong(0)
    private val totalSessionsDestroyed = AtomicLong(0)
    private val cacheHits = AtomicLong(0)
    private val cacheMisses = AtomicLong(0)

    /**
     * 세션 추가 (캐싱 기능 통합)
     */
    fun addSession(chatRoomId: String, session: WebSocketSession, userId: Long) {
        val sessionId = session.id
        
        // 세션 등록
        sessions[sessionId] = session
        sessionMetadata[sessionId] = SessionMetadata(
            sessionId = sessionId,
            userId = userId,
            chatRoomId = chatRoomId,
            connectedAt = Instant.now(),
            lastActivityAt = Instant.now(),
            messageCount = 0,
            lastMessageAt = null
        )
        
        // 매핑 관계 설정
        sessionToChatRoom[sessionId] = chatRoomId
        sessionToUser[sessionId] = userId
        
        // 채팅방별 세션 그룹에 추가
        chatRoomSessions.computeIfAbsent(chatRoomId) { 
            ConcurrentHashMap.newKeySet() 
        }.add(sessionId)
        
        // 사용자별 세션 그룹에 추가
        userSessions.computeIfAbsent(userId) { 
            ConcurrentHashMap.newKeySet() 
        }.add(sessionId)
        
        // 통계 업데이트
        totalSessionsCreated.incrementAndGet()
        
        logger.info("Session added: {} (user: {}, room: {}) [Total: {}]", 
            sessionId, userId, chatRoomId, sessions.size)
    }

    /**
     * 세션 제거
     */
    fun removeSession(sessionId: String) {
        sessions.remove(sessionId)
        sessionMetadata.remove(sessionId)
        val chatRoomId = sessionToChatRoom.remove(sessionId)
        val userId = sessionToUser.remove(sessionId)
        
        // 채팅방별 세션 그룹에서 제거
        if (chatRoomId != null) {
            chatRoomSessions[chatRoomId]?.remove(sessionId)
            if (chatRoomSessions[chatRoomId]?.isEmpty() == true) {
                chatRoomSessions.remove(chatRoomId)
            }
        }
        
        // 사용자별 세션 그룹에서 제거
        if (userId != null) {
            userSessions[userId]?.remove(sessionId)
            if (userSessions[userId]?.isEmpty() == true) {
                userSessions.remove(userId)
            }
        }
        
        // 통계 업데이트
        totalSessionsDestroyed.incrementAndGet()
        
        logger.info("Session removed: {} (user: {}, room: {}) [Remaining: {}]", 
            sessionId, userId, chatRoomId, sessions.size)
    }

    /**
     * 채팅방별 세션 조회 (캐시 히트율 추적)
     */
    fun getSessionsByChatRoom(chatRoomId: String): List<WebSocketSession> {
        val sessionIds = chatRoomSessions[chatRoomId]
        if (sessionIds != null) {
            cacheHits.incrementAndGet()
            return sessionIds
                .mapNotNull { sessionId -> sessions[sessionId] }
                .filter { it.isOpen }
        } else {
            cacheMisses.incrementAndGet()
            return emptyList()
        }
    }

    /**
     * 사용자별 세션 조회 (캐시 히트율 추적)
     */
    fun getSessionsByUserId(userId: Long): List<WebSocketSession> {
        val sessionIds = userSessions[userId]
        if (sessionIds != null) {
            cacheHits.incrementAndGet()
            return sessionIds
                .mapNotNull { sessionId -> sessions[sessionId] }
                .filter { it.isOpen }
        } else {
            cacheMisses.incrementAndGet()
            return emptyList()
        }
    }

    /**
     * 세션 ID로 세션 조회 (캐시 히트율 추적)
     */
    fun getSessionById(sessionId: String): WebSocketSession? {
        val session = sessions[sessionId]
        if (session != null && session.isOpen) {
            cacheHits.incrementAndGet()
            return session
        } else {
            cacheMisses.incrementAndGet()
            return null
        }
    }

    /**
     * 모든 활성 세션 조회
     */
    fun getAllActiveSessions(): List<WebSocketSession> {
        return sessions.values.filter { it.isOpen }
    }
    
    /**
     * 채팅방의 모든 활성 세션 조회 (닫힌 세션은 즉시 제거)
     */
    fun getActiveChatRoomSessions(chatRoomId: String): List<WebSocketSession> {
        val roomSessions = chatRoomSessions[chatRoomId] ?: return emptyList()
        val activeSessions = mutableListOf<WebSocketSession>()
        val closedSessionIds = mutableListOf<String>()
        
        for (sessionId in roomSessions) {
            val session = sessions[sessionId]
            if (session != null && session.isOpen) {
                activeSessions.add(session)
            } else {
                // 닫힌 세션은 정리 대상으로 표시
                closedSessionIds.add(sessionId)
            }
        }
        
        // 닫힌 세션들 즉시 정리
        closedSessionIds.forEach { sessionId ->
            logger.debug("Removing closed session {} from room {}", sessionId, chatRoomId)
            removeSession(sessionId)
        }
        
        return activeSessions
    }

    /**
     * 세션 ID로 채팅방 ID 조회
     */
    fun getChatRoomBySessionId(sessionId: String): String? {
        return sessionToChatRoom[sessionId]
    }

    /**
     * 세션 ID로 사용자 ID 조회
     */
    fun getUserBySessionId(sessionId: String): Long? {
        return sessionToUser[sessionId]
    }

    /**
     * 세션 메타데이터 조회
     */
    fun getSessionMetadata(sessionId: String): SessionMetadata? {
        return sessionMetadata[sessionId]
    }

    /**
     * 세션 활동 시간 업데이트 (메시지 카운트 포함)
     */
    fun updateSessionActivity(sessionId: String) {
        sessionMetadata[sessionId]?.let { metadata ->
            sessionMetadata[sessionId] = metadata.copy(
                lastActivityAt = Instant.now(),
                messageCount = metadata.messageCount + 1,
                lastMessageAt = Instant.now()
            )
        }
    }

    fun handleHeartbeat(sessionId: String) {
        sessionMetadata[sessionId]?.let { metadata ->
            sessionMetadata[sessionId] = metadata.copy(
                lastActivityAt = Instant.now()
            )
        }
    }

    fun updateSessionStatus(sessionId: String, status: String) {
        sessionMetadata[sessionId]?.let { metadata ->
            sessionMetadata[sessionId] = metadata.copy(status = status)
        }
    }
    
    /**
     * 캐시 통계 조회
     */
    fun getCacheStatistics(): Map<String, Any> {
        val totalRequests = cacheHits.get() + cacheMisses.get()
        val hitRate = if (totalRequests > 0) {
            (cacheHits.get().toDouble() / totalRequests * 100)
        } else 0.0
        
        return mapOf(
            "total_requests" to totalRequests,
            "cache_hits" to cacheHits.get(),
            "cache_misses" to cacheMisses.get(),
            "hit_rate_percentage" to String.format("%.2f", hitRate),
            "sessions_created" to totalSessionsCreated.get(),
            "sessions_destroyed" to totalSessionsDestroyed.get(),
            "active_sessions" to sessions.size,
            "cache_size" to sessions.size
        )
    }
    
    /**
     * 세션 통계 리셋
     */
    fun resetStatistics() {
        cacheHits.set(0)
        cacheMisses.set(0)
        totalSessionsCreated.set(0)
        totalSessionsDestroyed.set(0)
        logger.info("Session statistics reset")
    }

    /**
     * 채팅방 참가자 수 조회
     */
    fun getRoomParticipantCount(chatRoomId: String): Int {
        return getSessionsByChatRoom(chatRoomId).size
    }

    /**
     * 사용자의 활성 세션 수 조회
     */
    fun getUserActiveSessionCount(userId: Long): Int {
        return getSessionsByUserId(userId).size
    }

    /**
     * 전체 통계 조회
     */
    fun getConnectionStats(): ConnectionStats {
        val activeSessions = sessions.values.count { it.isOpen }
        val totalRooms = chatRoomSessions.size
        val totalUsers = userSessions.size
        
        return ConnectionStats(
            activeConnections = activeSessions,
            totalRooms = totalRooms,
            totalUsers = totalUsers,
            averageSessionsPerUser = if (totalUsers > 0) activeSessions.toDouble() / totalUsers else 0.0
        )
    }

    /**
     * 주기적 정리 작업 (닫힌 세션 제거)
     */
    @Scheduled(fixedRate = 60000) // 1분마다 실행
    fun cleanupClosedSessions() {
        logger.debug("Running scheduled cleanup of closed WebSocket sessions")
        
        val closedSessionIds = sessions.filter { (_, session) -> !session.isOpen }.keys
        
        if (closedSessionIds.isNotEmpty()) {
            logger.info("Found {} closed sessions to clean up", closedSessionIds.size)
            closedSessionIds.forEach { sessionId ->
                removeSession(sessionId)
            }
        }
    }

    /**
     * 비활성 세션 정리 (일정 시간 동안 활동이 없는 세션)
     */
    @Scheduled(fixedRate = 300000) // 5분마다 실행
    fun cleanupInactiveSessions() {
        val now = Instant.now()
        val inactivityThreshold = now.minusSeconds(1800) // 30분
        
        val inactiveSessionIds = sessionMetadata
            .filter { (_, metadata) -> metadata.lastActivityAt.isBefore(inactivityThreshold) }
            .keys
        
        if (inactiveSessionIds.isNotEmpty()) {
            logger.info("Found {} inactive sessions to clean up", inactiveSessionIds.size)
            inactiveSessionIds.forEach { sessionId ->
                sessions[sessionId]?.close()
                removeSession(sessionId)
            }
        }
    }
}
