package com.simplechat.controller

import com.simplechat.dto.common.ApiResponse
import com.simplechat.infrastructure.monitoring.model.ErrorStatistics
import com.simplechat.infrastructure.monitoring.model.SessionErrorInfo
import com.simplechat.infrastructure.websocket.handler.WebSocketErrorHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * WebSocket 에러 처리 및 모니터링을 위한 REST API 컨트롤러
 * 
 * 관리자가 WebSocket 에러 통계를 확인하고 문제가 있는 세션을 모니터링할 수 있습니다.
 */
@RestController
@RequestMapping("/api/websocket/errors")
class WebSocketErrorController(
    private val errorHandler: WebSocketErrorHandler
) {

    /**
     * 전체 WebSocket 에러 통계를 조회합니다.
     */
    @GetMapping("/statistics")
    fun getErrorStatistics(): ApiResponse<ErrorStatistics> {
        val statistics = errorHandler.getErrorStatistics()
        return ApiResponse.success(statistics, "WebSocket 에러 통계를 조회했습니다.")
    }

    /**
     * 특정 세션의 에러 정보를 조회합니다.
     */
    @GetMapping("/sessions/{sessionId}")
    fun getSessionErrorInfo(@PathVariable sessionId: String): ApiResponse<SessionErrorInfo?> {
        val sessionInfo = errorHandler.getSessionErrorInfo(sessionId)
        
        return if (sessionInfo != null) {
            ApiResponse.success(sessionInfo, "세션 에러 정보를 조회했습니다.")
        } else {
            ApiResponse.success(null, "해당 세션의 에러 정보가 없습니다.")
        }
    }

    /**
     * WebSocket 에러 처리 시스템의 상태를 확인합니다.
     * 관리자용 헬스체크 엔드포인트
     */
    @GetMapping("/health")
    fun getErrorSystemHealth(): ApiResponse<Map<String, Any>> {
        val statistics = errorHandler.getErrorStatistics()
        
        val healthStatus = when {
            statistics.errorsInLastMinute > 50 -> "CRITICAL"
            statistics.errorsInLastMinute > 20 -> "WARNING"
            statistics.errorsInLastHour > 100 -> "CAUTION"
            else -> "HEALTHY"
        }
        
        val healthInfo = mapOf(
            "status" to healthStatus,
            "totalErrors" to statistics.totalErrors,
            "errorsInLastHour" to statistics.errorsInLastHour,
            "errorsInLastMinute" to statistics.errorsInLastMinute,
            "activeProblematicSessions" to statistics.activeSessionsWithErrors,
            "timestamp" to System.currentTimeMillis()
        )
        
        return ApiResponse.success(healthInfo, "WebSocket 에러 처리 시스템 상태입니다.")
    }

    /**
     * 문제가 있는 세션들의 목록을 조회합니다.
     */
    @GetMapping("/problem-sessions")
    fun getProblemSessions(): ApiResponse<Map<String, Long>> {
        val statistics = errorHandler.getErrorStatistics()
        return ApiResponse.success(statistics.problemSessions, "문제가 있는 세션 목록입니다.")
    }

    /**
     * 에러 코드별 발생 통계를 조회합니다.
     */
    @GetMapping("/by-error-code")
    fun getErrorsByCode(): ApiResponse<Map<String, Long>> {
        val statistics = errorHandler.getErrorStatistics()
        return ApiResponse.success(statistics.errorsByCode, "에러 코드별 발생 통계입니다.")
    }
}