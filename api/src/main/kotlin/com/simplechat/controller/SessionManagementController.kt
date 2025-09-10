package com.simplechat.controller

import com.simplechat.dto.common.ApiResponse
import com.simplechat.security.JwtAuthenticationHelper
import com.simplechat.service.AuthService
import com.simplechat.service.SessionInvalidationService
import com.simplechat.dto.session.UserSessionInfo
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ServerWebExchange
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

/**
 * 사용자 세션 관리 REST API 컨트롤러
 */
@RestController
@RequestMapping("/api/sessions")
@Tag(name = "세션 관리", description = "사용자 활성 세션 조회 및 관리 API")
@SecurityRequirement(name = "bearerAuth")
class SessionManagementController(
    private val authService: AuthService,
    private val sessionInvalidationService: SessionInvalidationService,
    private val jwtAuthenticationHelper: JwtAuthenticationHelper
) {

    /**
     * 현재 사용자의 활성 세션 목록 조회
     */
    @GetMapping("/active")
    @Operation(
        summary = "활성 세션 조회",
        description = "현재 사용자의 모든 활성 WebSocket 세션 정보를 조회합니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
            SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
            SwaggerApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
        ]
    )
    suspend fun getActiveSessions(exchange: ServerWebExchange): ResponseEntity<ApiResponse<UserSessionInfo>> {
        val token = jwtAuthenticationHelper.validateTokenFromExchange(exchange)
        val email = jwtAuthenticationHelper.getEmailFromToken(token)
        
        val sessionInfo = authService.getUserActiveSessions(email)
        
        return ResponseEntity.ok(
            ApiResponse.success(
                sessionInfo,
                "활성 세션 정보를 성공적으로 조회했습니다."
            )
        )
    }

    /**
     * 특정 세션 강제 종료
     */
    @DeleteMapping("/{sessionId}")
    @Operation(
        summary = "세션 강제 종료",
        description = "지정된 세션을 강제로 종료합니다. 본인의 세션만 종료할 수 있습니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "세션 종료 성공"),
            SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
            SwaggerApiResponse(responseCode = "403", description = "권한 없음 (본인 세션이 아님)"),
            SwaggerApiResponse(responseCode = "404", description = "세션을 찾을 수 없음")
        ]
    )
    suspend fun terminateSession(
        @Parameter(description = "종료할 세션 ID", required = true)
        @PathVariable sessionId: String,
        exchange: ServerWebExchange
    ): ResponseEntity<ApiResponse<Map<String, Boolean>>> {
        val token = jwtAuthenticationHelper.validateTokenFromExchange(exchange)
        val email = jwtAuthenticationHelper.getEmailFromToken(token)
        
        val result = authService.forceLogoutSession(email, sessionId)
        
        return ResponseEntity.ok(
            ApiResponse.success(
                mapOf("terminated" to result),
                if (result) "세션이 성공적으로 종료되었습니다." else "세션 종료에 실패했습니다."
            )
        )
    }

    /**
     * 현재 세션 제외한 모든 다른 세션 강제 종료
     */
    @DeleteMapping("/others")
    @Operation(
        summary = "다른 세션 모두 강제 종료",
        description = "현재 세션을 제외한 사용자의 모든 다른 활성 세션을 강제로 종료합니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "세션 종료 성공"),
            SwaggerApiResponse(responseCode = "401", description = "인증 실패")
        ]
    )
    suspend fun terminateOtherSessions(
        @Parameter(description = "현재 세션 ID (선택적)", required = false)
        @RequestParam(required = false) currentSessionId: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<ApiResponse<Map<String, Int>>> {
        val token = jwtAuthenticationHelper.validateTokenFromExchange(exchange)
        val email = jwtAuthenticationHelper.getEmailFromToken(token)
        
        val terminatedCount = authService.forceLogoutAllOtherSessions(email, currentSessionId)
        
        return ResponseEntity.ok(
            ApiResponse.success(
                mapOf("terminatedSessions" to terminatedCount),
                "${terminatedCount}개의 세션이 성공적으로 종료되었습니다."
            )
        )
    }

    /**
     * 세션 통계 조회
     */
    @GetMapping("/stats")
    @Operation(
        summary = "세션 통계 조회",
        description = "현재 사용자의 세션 통계 정보를 조회합니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
            SwaggerApiResponse(responseCode = "401", description = "인증 실패")
        ]
    )
    suspend fun getSessionStats(exchange: ServerWebExchange): ResponseEntity<ApiResponse<Map<String, Any>>> {
        val token = jwtAuthenticationHelper.validateTokenFromExchange(exchange)
        val email = jwtAuthenticationHelper.getEmailFromToken(token)
        val userId = jwtAuthenticationHelper.getUserIdFromToken(token)
        
        val sessionInfo = authService.getUserActiveSessions(email)
        
        val stats = mapOf(
            "totalSessions" to sessionInfo.totalSessions,
            "activeSessions" to sessionInfo.activeSessions,
            "userId" to userId,
            "hasMultipleSessions" to (sessionInfo.activeSessions > 1),
            "sessionDetails" to sessionInfo.sessions.map { session ->
                mapOf(
                    "sessionId" to session.sessionId,
                    "chatRoomId" to session.chatRoomId,
                    "connectedAt" to session.connectedAt,
                    "lastActivityAt" to session.lastActivityAt,
                    "messageCount" to session.messageCount,
                    "isActive" to session.isActive
                )
            }
        )
        
        return ResponseEntity.ok(
            ApiResponse.success(stats, "세션 통계를 성공적으로 조회했습니다.")
        )
    }
}