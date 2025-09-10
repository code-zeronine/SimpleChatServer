package com.simplechat.controller

import com.simplechat.dto.common.ApiResponse
import com.simplechat.infrastructure.security.WebSocketAuthService
import com.simplechat.infrastructure.session.WebSocketSessionManager
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * WebSocket 모니터링 컨트롤러
 * 
 * WebSocket 연결 상태, 통계 정보를 제공하는 관리자 전용 엔드포인트입니다.
 */
@RestController
@RequestMapping("/api/admin/websocket")
class WebSocketMonitoringController(
    private val webSocketAuthService: WebSocketAuthService,
    private val sessionManager: WebSocketSessionManager
) {

    private val logger = LoggerFactory.getLogger(WebSocketMonitoringController::class.java)

    /**
     * WebSocket 연결 통계 조회
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    fun getWebSocketStats(): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            val connectionStats = webSocketAuthService.getConnectionStats()
            val sessionStats = sessionManager.getCacheStatistics()
            
            val combinedStats = mapOf(
                "connections" to connectionStats,
                "sessions" to sessionStats,
                "timestamp" to System.currentTimeMillis()
            )
            
            ResponseEntity.ok(ApiResponse.success(combinedStats))
            
        } catch (e: Exception) {
            logger.error("Error retrieving WebSocket statistics: {}", e.message, e)
            ResponseEntity.internalServerError()
                .body(ApiResponse.error<Map<String, Any>>(
                    "WEBSOCKET_STATS_ERROR", 
                    "Failed to retrieve WebSocket statistics"
                ))
        }
    }

    /**
     * WebSocket 세션 상세 정보 조회
     */
    @GetMapping("/sessions")
    @PreAuthorize("hasRole('ADMIN')")
    fun getWebSocketSessions(): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            val allSessions = sessionManager.getAllActiveSessions()
            val sessionsInfo = allSessions.mapNotNull { session ->
                sessionManager.getSessionMetadata(session.id)?.let {
                    mapOf(
                        "sessionId" to it.sessionId,
                        "userId" to it.userId,
                        "chatRoomId" to it.chatRoomId,
                        "connectedAt" to it.connectedAt.toString(),
                        "lastActivityAt" to it.lastActivityAt.toString(),
                        "messageCount" to it.messageCount,
                        "status" to it.status
                    )
                }
            }

            val sessionInfo = mapOf(
                "sessions" to sessionsInfo,
                "totalSessions" to allSessions.size,
                "timestamp" to System.currentTimeMillis()
            )

            ResponseEntity.ok(ApiResponse.success(sessionInfo))

        } catch (e: Exception) {
            logger.error("Error retrieving WebSocket session information: {}", e.message, e)
            ResponseEntity.internalServerError()
                .body(ApiResponse.error<Map<String, Any>>(
                    "WEBSOCKET_SESSIONS_ERROR",
                    "Failed to retrieve WebSocket session information"
                ))
        }
    }

    /**
     * WebSocket 연결 상태 헬스체크
     */
    @GetMapping("/health")
    fun getWebSocketHealth(): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            val connectionStats = webSocketAuthService.getConnectionStats()
            val totalConnections = connectionStats["totalConnections"] as? Int ?: 0
            val maxConnections = connectionStats["maxConnections"] as? Int ?: 1000
            
            val utilizationPercentage = if (maxConnections > 0) {
                (totalConnections.toDouble() / maxConnections * 100).toInt()
            } else {
                0
            }
            
            val status = when {
                utilizationPercentage < 70 -> "HEALTHY"
                utilizationPercentage < 90 -> "WARNING"
                else -> "CRITICAL"
            }
            
            val healthInfo = mapOf(
                "status" to status,
                "totalConnections" to totalConnections,
                "maxConnections" to maxConnections,
                "utilizationPercentage" to utilizationPercentage,
                "activeUsers" to (connectionStats["activeUsers"] as? Int ?: 0),
                "timestamp" to System.currentTimeMillis()
            )
            
            ResponseEntity.ok(ApiResponse.success(healthInfo))
            
        } catch (e: Exception) {
            logger.error("Error checking WebSocket health: {}", e.message, e)
            ResponseEntity.internalServerError()
                .body(ApiResponse.error<Map<String, Any>>(
                    "WEBSOCKET_HEALTH_ERROR",
                    "Failed to check WebSocket health"
                ))
        }
    }
}