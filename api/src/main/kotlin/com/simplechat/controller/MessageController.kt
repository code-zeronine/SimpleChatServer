package com.simplechat.controller

import com.simplechat.dto.common.ApiResponse
import com.simplechat.dto.common.PagedApiResponse
import com.simplechat.dto.message.MessageDto
import com.simplechat.service.MessageService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

/**
 * 메시지 관리를 위한 REST API 컨트롤러
 *
 * 채팅방 메시지 조회, 검색, 캐시 관리 기능을 제공합니다.
 */
@RestController
@RequestMapping("/api/messages")
@Tag(name = "메시지 관리", description = "채팅방 메시지 조회, 검색, 캐시 관리 API")
@SecurityRequirement(name = "bearerAuth")
class MessageController(
    private val messageService: MessageService
) {

    @GetMapping("/room/{roomId}")
    @Operation(
        summary = "채팅방 메시지 조회",
        description = "특정 채팅방의 메시지를 페이지네이션으로 조회합니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "메시지 조회 성공"),
            SwaggerApiResponse(responseCode = "400", description = "잘못된 요청 파라미터"),
            SwaggerApiResponse(responseCode = "403", description = "접근 권한 없음"),
            SwaggerApiResponse(responseCode = "404", description = "채팅방을 찾을 수 없음")
        ]
    )
    suspend fun getMessagesByRoom(
        @Parameter(description = "채팅방 ID", required = true, example = "1")
        @PathVariable roomId: Long,
        @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기", example = "50")
        @RequestParam(defaultValue = "50") size: Int
    ): PagedApiResponse<MessageDto> {
        require(page >= 0) { "page must be greater than or equal to 0" }
        require(size > 0) { "size must be greater than 0" }
        return messageService.getMessagesByRoom(roomId, page, size)
    }

    @GetMapping("/room/{roomId}/recent")
    @Operation(
        summary = "최근 메시지 조회",
        description = "특정 채팅방의 최근 메시지를 조회합니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "최근 메시지 조회 성공"),
            SwaggerApiResponse(responseCode = "400", description = "잘못된 요청 파라미터"),
            SwaggerApiResponse(responseCode = "403", description = "접근 권한 없음"),
            SwaggerApiResponse(responseCode = "404", description = "채팅방을 찾을 수 없음")
        ]
    )
    suspend fun getRecentMessages(
        @Parameter(description = "채팅방 ID", required = true, example = "1")
        @PathVariable roomId: Long,
        @Parameter(description = "조회할 메시지 개수", example = "10")
        @RequestParam(defaultValue = "10") size: Int
    ): List<MessageDto> {
        require(size > 0) { "size must be greater than 0" }
        return messageService.getRecentMessages(roomId, size)
    }

    @GetMapping("/room/{roomId}/count")
    @Operation(
        summary = "메시지 개수 조회",
        description = "특정 채팅방의 총 메시지 개수를 조회합니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "메시지 개수 조회 성공"),
            SwaggerApiResponse(responseCode = "400", description = "잘못된 요청 파라미터"),
            SwaggerApiResponse(responseCode = "403", description = "접근 권한 없음"),
            SwaggerApiResponse(responseCode = "404", description = "채팅방을 찾을 수 없음")
        ]
    )
    suspend fun countMessages(
        @Parameter(description = "채팅방 ID", required = true, example = "1")
        @PathVariable roomId: Long
    ): ApiResponse<Map<String, Long>> {
        val count = messageService.countMessages(roomId)
        return ApiResponse.success(mapOf("count" to count))
    }

    @GetMapping("/search")
    @Operation(
        summary = "메시지 검색",
        description = "다양한 필터를 사용하여 메시지를 검색합니다. 키워드, 사용자, 메시지 타입, 날짜 범위로 필터링 가능합니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "메시지 검색 성공"),
            SwaggerApiResponse(responseCode = "400", description = "잘못된 검색 파라미터")
        ]
    )
    suspend fun searchMessages(
        @Parameter(description = "검색할 채팅방 ID", example = "1")
        @RequestParam roomId: Long?,
        @Parameter(description = "검색 키워드", example = "안녕하세요")
        @RequestParam keyword: String?,
        @Parameter(description = "사용자 ID", example = "1")
        @RequestParam userId: Long?,
        @Parameter(description = "메시지 타입", example = "TEXT")
        @RequestParam messageType: String?,
        @Parameter(description = "시작 날짜 (ISO 8601)", example = "2024-01-01T00:00:00Z")
        @RequestParam startDate: String?,
        @Parameter(description = "종료 날짜 (ISO 8601)", example = "2024-12-31T23:59:59Z")
        @RequestParam endDate: String?,
        @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기", example = "50")
        @RequestParam(defaultValue = "50") size: Int
    ): PagedApiResponse<MessageDto> {
        return messageService.searchMessages(roomId, keyword, userId, messageType, startDate, endDate, page, size)
    }

    @GetMapping("/room/{roomId}/cache/stats")
    @Operation(
        summary = "캐시 통계 조회",
        description = "특정 채팅방의 에 캐시 통계 정보(히트 비율, 캐시 성능)를 조회합니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "캐시 통계 조회 성공"),
            SwaggerApiResponse(responseCode = "400", description = "잘못된 요청 파라미터"),
            SwaggerApiResponse(responseCode = "403", description = "접근 권한 없음"),
            SwaggerApiResponse(responseCode = "404", description = "채팅방을 찾을 수 없음")
        ]
    )
    suspend fun getCacheStats(
        @Parameter(description = "채팅방 ID", required = true, example = "1")
        @PathVariable roomId: Long
    ): ApiResponse<Map<String, Any>>? {
        return messageService.getCacheStats(roomId)?.let { stats ->
            ApiResponse.success(
                mapOf(
                    "roomId" to stats.roomId,
                    "cacheHits" to stats.hits,
                    "cacheMisses" to stats.misses,
                    "hitRate" to String.format("%.2f%%", stats.hitRate)
                )
            )
        }
    }

    @DeleteMapping("/room/{roomId}/cache")
    @Operation(
        summary = "캐시 무효화",
        description = "특정 채팅방의 캐시를 무효화합니다. 새로운 메시지를 즉시 반영하고자 할 때 사용합니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "캐시 무효화 성공"),
            SwaggerApiResponse(responseCode = "400", description = "잘못된 요청 파라미터"),
            SwaggerApiResponse(responseCode = "403", description = "접근 권한 없음"),
            SwaggerApiResponse(responseCode = "404", description = "채팅방을 찾을 수 없음")
        ]
    )
    suspend fun invalidateCache(
        @Parameter(description = "채팅방 ID", required = true, example = "1")
        @PathVariable roomId: Long
    ): ApiResponse<Map<String, String>> {
        messageService.invalidateRoomCache(roomId)
        return ApiResponse.success(
            mapOf("message" to "Cache invalidated successfully for room $roomId")
        )
    }
}
