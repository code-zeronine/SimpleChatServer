package com.simplechat.controller

import com.simplechat.dto.ApiResponse
import com.simplechat.dto.UserDto
import com.simplechat.security.JwtAuthenticationHelper
import com.simplechat.service.AuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

/**
 * 인증이 필요한 사용자 관련 API 컨트롤러
 */
@RestController
@RequestMapping("/api/users")
@Tag(name = "사용자 관리", description = "인증된 사용자 정보 조회 및 프로필 관리 API")
@SecurityRequirement(name = "bearerAuth")
class UserController(
    private val authService: AuthService,
    private val jwtAuthenticationHelper: JwtAuthenticationHelper
) {

    /**
     * 현재 로그인한 사용자 정보 조회
     */
    @GetMapping("/me")
    @Operation(
        summary = "내 정보 조회",
        description = "현재 로그인한 사용자의 기본 정보를 조회합니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "사용자 정보 조회 성공"),
            SwaggerApiResponse(responseCode = "401", description = "인증 실패 또는 만료된 토큰")
        ]
    )
    suspend fun getCurrentUser(
        @Parameter(description = "JWT 인증 토큰", required = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authHeader: String
    ): ResponseEntity<ApiResponse<UserDto>> {
        val token = jwtAuthenticationHelper.extractTokenFromHeader(authHeader).awaitSingle()
        jwtAuthenticationHelper.validateToken(token).awaitSingle()
        val user = authService.validateUser(token)
        return ResponseEntity.ok(ApiResponse.success(user, "사용자 정보를 성공적으로 조회하였습니다."))
    }

    /**
     * 사용자 프로필 업데이트 (예시 엔드포인트)
     */
    @PutMapping("/profile")
    @Operation(
        summary = "프로필 업데이트",
        description = "현재 사용자의 프로필 정보를 업데이트합니다. (현재는 예시 구현)"
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "프로필 업데이트 성공"),
            SwaggerApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
            SwaggerApiResponse(responseCode = "401", description = "인증 실패 또는 만료된 토큰")
        ]
    )
    suspend fun updateProfile(
        @Parameter(description = "JWT 인증 토큰", required = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authHeader: String,
        @Parameter(description = "업데이트할 프로필 정보", required = true)
        @RequestBody updateRequest: Map<String, String>
    ): ResponseEntity<ApiResponse<UserDto>> {
        val newNickname = updateRequest["nickname"] ?: throw Exception("Nickname is required")
        val token = jwtAuthenticationHelper.extractTokenFromHeader(authHeader).awaitSingle()
        jwtAuthenticationHelper.validateToken(token).awaitSingle()
        val email = jwtAuthenticationHelper.getEmailFromToken(token).awaitSingle()
        val updatedUser = authService.updateNickname(email, newNickname)
        return ResponseEntity.ok(ApiResponse.success(updatedUser, "프로필이 성공적으로 수정되었습니다."))
    }
}