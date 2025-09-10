package com.simplechat.controller

import com.simplechat.domain.entity.ChatRoomRole
import com.simplechat.domain.repository.ChatRoomRepository
import com.simplechat.domain.repository.UserRepository
import com.simplechat.dto.common.ApiResponse
import com.simplechat.dto.participant.ChangeParticipantRoleRequest
import com.simplechat.dto.chatroom.ChatRoomDetailsDto
import com.simplechat.dto.chatroom.ChatRoomDto
import com.simplechat.dto.chatroom.ChatRoomListResponse
import com.simplechat.dto.chatroom.CreateChatRoomRequest
import com.simplechat.dto.participant.KickParticipantRequest
import com.simplechat.dto.chatroom.ParticipantDto
import com.simplechat.dto.chatroom.UpdateChatRoomRequest
import com.simplechat.infrastructure.session.WebSocketSessionManager
import com.simplechat.security.JwtAuthenticationHelper
import com.simplechat.service.ChatRoomService
import com.simplechat.service.MessageService
import com.simplechat.service.UserChatRoomService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

/**
 * 채팅방 관리를 위한 REST API 컨트롤러
 *
 * 채팅방 생성, 조회, 참여, 퇴장 및 참여자 관리 기능을 제공합니다.
 */
@RestController
@RequestMapping("/api/rooms")
@Tag(name = "채팅방 관리", description = "채팅방 생성, 조회, 참여, 퇴장 및 참여자 관리 API")
@SecurityRequirement(name = "bearerAuth")
class ChatRoomController(
    private val chatRoomService: ChatRoomService,
    private val userChatRoomService: UserChatRoomService,
    private val messageService: MessageService, // MessageService 의존성 주입
    private val jwtAuthenticationHelper: JwtAuthenticationHelper,
    private val userRepository: UserRepository,
    private val webSocketSessionManager: WebSocketSessionManager,
    private val chatRoomRepository: ChatRoomRepository // Inject repository for counting
) {

    /**
     * 새로운 채팅방을 생성합니다.
     */
    @PostMapping
    @Operation(
        summary = "채팅방 생성",
        description = "새로운 채팅방을 생성합니다. 생성한 사용자가 자동으로 소유자(OWNER)가 됩니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "201", description = "채팅방 생성 성공"),
            SwaggerApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
            SwaggerApiResponse(responseCode = "401", description = "인증 실패")
        ]
    )
    suspend fun createChatRoom(
        @Parameter(description = "JWT 인증 토큰", required = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authHeader: String,
        @Parameter(description = "채팅방 생성 요청 데이터", required = true)
        @Valid @RequestBody request: CreateChatRoomRequest
    ): ResponseEntity<ApiResponse<ChatRoomDto>> {
        val userId = extractUserIdFromToken(authHeader)
        val chatRoom = chatRoomService.createChatRoom(
            name = request.name,
            description = request.description,
            isPrivate = request.isPrivate,
            maxParticipants = request.maxParticipants,
            ownerId = userId
        )
        val participantCount = userChatRoomService.countActiveParticipants(chatRoom.id!!).awaitSingle()
        val chatRoomDto = ChatRoomDto.from(chatRoom, participantCount.toInt(), true)
        
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.success(chatRoomDto, "채팅방이 성공적으로 생성되었습니다."))
    }

    /**
     * 채팅방 목록을 조회합니다.
     */
    @GetMapping
    @Operation(
        summary = "채팅방 목록 조회",
        description = "채팅방 목록을 페이지네이션으로 조회합니다. 필터(all, joined)를 사용할 수 있습니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
            SwaggerApiResponse(responseCode = "401", description = "인증 실패")
        ]
    )
    suspend fun getChatRooms(
        @Parameter(description = "JWT 인증 토큰", required = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authHeader: String,
        @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기", example = "20")
        @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "필터 (all, joined)", example = "all")
        @RequestParam(defaultValue = "all") filter: String
    ): ResponseEntity<ApiResponse<ChatRoomListResponse>> {
        val userId = extractUserIdFromToken(authHeader)
        
        val roomsFlow = when (filter) {
            "joined" -> {
                val userRooms = userChatRoomService.getUserActiveRooms(userId)
                userRooms.flatMap { userChatRoom -> 
                    chatRoomService.findChatRoomById(userChatRoom.chatRoomId) 
                }.asFlow()
            }
            else -> chatRoomService.getPublicRooms(size * (page + 1)).asFlow()
        }

        val roomsWithDetails = roomsFlow.map { room ->
            coroutineScope {
                val countDeferred = async { userChatRoomService.countActiveParticipants(room.id!!).awaitSingle() }
                val isJoinedDeferred = async { userChatRoomService.isActiveParticipant(userId, room.id!!).awaitSingle() }
                val latestMessageDeferred = async { messageService.getRecentMessages(room.id!!, 1).firstOrNull() }

                ChatRoomDto.from(
                    room,
                    countDeferred.await().toInt(),
                    isJoinedDeferred.await(),
                    latestMessageDeferred.await()
                )
            }
        }.toList()

        val paginatedRooms = roomsWithDetails.drop(page * size).take(size)

        val totalCount = when (filter) {
            "joined" -> userChatRoomService.countUserActiveRooms(userId).awaitSingle()
            else -> chatRoomRepository.count().awaitSingle()
        }.toInt()

        val hasNext = (page + 1) * size < totalCount

        val response = ChatRoomListResponse(
            rooms = paginatedRooms,
            totalCount = totalCount.toInt(),
            page = page,
            size = size,
            hasNext = hasNext
        )
        return ResponseEntity.ok(ApiResponse.success(response))
    }

    /**
     * 특정 채팅방의 상세 정보를 조회합니다.
     */
    @GetMapping("/{roomId}")
    @Operation(
        summary = "채팅방 상세 조회",
        description = "특정 채팅방의 상세 정보와 참여자 목록을 조회합니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
            SwaggerApiResponse(responseCode = "403", description = "접근 권한 없음"),
            SwaggerApiResponse(responseCode = "404", description = "채팅방을 찾을 수 없음")
        ]
    )
    suspend fun getChatRoomDetails(
        @Parameter(description = "JWT 인증 토큰", required = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authHeader: String,
        @Parameter(description = "채팅방 ID", required = true, example = "1")
        @PathVariable roomId: Long
    ): ResponseEntity<ApiResponse<ChatRoomDetailsDto>> {
        val userId = extractUserIdFromToken(authHeader)
        val details = chatRoomService.getChatRoomDetails(roomId, userId)
        
        val isJoined = details.userRelationship != null && details.userRelationship.isActive
        val roomDto = ChatRoomDto.from(details.room, details.participantCount, isJoined)

        val participants = coroutineScope {
            details.participants.map { ucr ->
                async {
                    val user = userRepository.findById(ucr.userId).awaitSingle()
                    val isOnline = webSocketSessionManager.getUserActiveSessionCount(ucr.userId) > 0
                    ParticipantDto.from(ucr, user.nickname, isOnline)
                }
            }.map { it.await() }
        }

        val ownerRelationship = details.participants.find { it.userId == details.room.createdBy }!!
        val ownerUser = userRepository.findById(details.room.createdBy).awaitSingle()
        val isOwnerOnline = webSocketSessionManager.getUserActiveSessionCount(ownerUser.id!!) > 0
        val ownerDto = ParticipantDto.from(ownerRelationship, ownerUser.nickname, isOwnerOnline)

        val detailsDto = ChatRoomDetailsDto(
            room = roomDto,
            participants = participants,
            admins = participants.filter { it.role == ChatRoomRole.ADMIN },
            owner = ownerDto
        )
        
        return ResponseEntity.ok(ApiResponse.success(detailsDto))
    }


    /**
     * 채팅방에 참여합니다.
     */
    @PostMapping("/{roomId}/join")
    @Operation(
        summary = "채팅방 참여",
        description = "지정된 채팅방에 참여합니다. 비공개 채팅방의 경우 초대가 필요할 수 있습니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "참여 성공"),
            SwaggerApiResponse(responseCode = "403", description = "참여 권한 없음"),
            SwaggerApiResponse(responseCode = "404", description = "채팅방을 찾을 수 없음"),
            SwaggerApiResponse(responseCode = "409", description = "이미 참여한 채팅방")
        ]
    )
    suspend fun joinChatRoom(
        @Parameter(description = "JWT 인증 토큰", required = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authHeader: String,
        @Parameter(description = "참여할 채팅방 ID", required = true, example = "1")
        @PathVariable roomId: Long
    ): ResponseEntity<ApiResponse<Unit>> {
        val userId = extractUserIdFromToken(authHeader)
        chatRoomService.joinChatRoom(userId, roomId)
        return ResponseEntity.ok(ApiResponse.success("채팅방에 성공적으로 참여했습니다."))
    }

    /**
     * 채팅방에서 퇴장합니다.
     */
    @DeleteMapping("/{roomId}/leave", produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "채팅방 퇴장",
        description = "현재 참여한 채팅방에서 퇴장합니다. 소유자는 퇴장할 수 없습니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "퇴장 성공"),
            SwaggerApiResponse(responseCode = "403", description = "퇴장 권한 없음 (소유자는 퇴장 불가)"),
            SwaggerApiResponse(responseCode = "404", description = "채팅방을 찾을 수 없음")
        ]
    )
    suspend fun leaveChatRoom(
        @Parameter(description = "JWT 인증 토큰", required = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authHeader: String,
        @Parameter(description = "퇴장할 채팅방 ID", required = true, example = "1")
        @PathVariable roomId: Long
    ): ResponseEntity<ApiResponse<Unit>> {
        val userId = extractUserIdFromToken(authHeader)
        chatRoomService.leaveChatRoom(userId, roomId)
        return ResponseEntity.ok(ApiResponse.success("채팅방에서 성공적으로 퇴장했습니다."))
    }

    /**
     * 채팅방 참여자 목록을 조회합니다.
     */
    @GetMapping("/{roomId}/participants")
    @Operation(
        summary = "참여자 목록 조회",
        description = "특정 채팅방의 활성 참여자 목록을 조회합니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "조회 성공"),
            SwaggerApiResponse(responseCode = "403", description = "접근 권한 없음"),
            SwaggerApiResponse(responseCode = "404", description = "채팅방을 찾을 수 없음")
        ]
    )
    suspend fun getChatRoomParticipants(
        @Parameter(description = "JWT 인증 토큰", required = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authHeader: String,
        @Parameter(description = "조회할 채팅방 ID", required = true, example = "1")
        @PathVariable roomId: Long
    ): ResponseEntity<ApiResponse<List<ParticipantDto>>> {
        val userId = extractUserIdFromToken(authHeader)
        val participantRelations = chatRoomService.getRoomParticipants(roomId, userId).collectList().awaitSingle()
        val participants = coroutineScope {
            participantRelations.map { userChatRoom ->
                async {
                    val user = userRepository.findById(userChatRoom.userId).awaitSingle()
                    val isOnline = webSocketSessionManager.getUserActiveSessionCount(userChatRoom.userId) > 0
                    ParticipantDto.from(userChatRoom, user.nickname, isOnline)
                }
            }.awaitAll()
        }
        return ResponseEntity.ok(ApiResponse.success(participants, "참여자 목록을 성공적으로 조회하였습니다."))
    }

    /**
     * 참여자 역할을 변경합니다. (관리자/소유자만 가능)
     */
    @PutMapping("/{roomId}/participants/role")
    @Operation(
        summary = "참여자 역할 변경",
        description = "채팅방 참여자의 역할을 변경합니다. 관리자 또는 소유자 권한이 필요합니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "역할 변경 성공"),
            SwaggerApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
            SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
            SwaggerApiResponse(responseCode = "403", description = "권한 없음 (관리자/소유자 권한 필요)"),
            SwaggerApiResponse(responseCode = "404", description = "채팅방 또는 사용자를 찾을 수 없음")
        ]
    )
    suspend fun changeParticipantRole(
        @Parameter(description = "JWT 인증 토큰", required = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authHeader: String,
        @Parameter(description = "채팅방 ID", required = true, example = "1")
        @PathVariable roomId: Long,
        @Parameter(description = "역할 변경 요청 데이터", required = true)
        @Valid @RequestBody request: ChangeParticipantRoleRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        val requesterId = extractUserIdFromToken(authHeader)
        chatRoomService.changeParticipantRole(
            requesterId = requesterId,
            targetUserId = request.targetUserId,
            roomId = roomId,
            newRole = request.newRole
        )
        return ResponseEntity.ok(ApiResponse.success("참여자 역할이 성공적으로 변경되었습니다."))
    }

    /**
     * 참여자를 추방합니다. (관리자/소유자만 가능)
     */
    @DeleteMapping("/{roomId}/participants/kick")
    @Operation(
        summary = "참여자 추방",
        description = "채팅방에서 특정 참여자를 추방합니다. 관리자 또는 소유자 권한이 필요합니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "추방 성공"),
            SwaggerApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
            SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
            SwaggerApiResponse(responseCode = "403", description = "권한 없음 (관리자/소유자 권한 필요)"),
            SwaggerApiResponse(responseCode = "404", description = "채팅방 또는 사용자를 찾을 수 없음"),
            SwaggerApiResponse(responseCode = "409", description = "추방할 수 없는 사용자 (소유자 등)")
        ]
    )
    suspend fun kickParticipant(
        @Parameter(description = "JWT 인증 토큰", required = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authHeader: String,
        @Parameter(description = "채팅방 ID", required = true, example = "1")
        @PathVariable roomId: Long,
        @Parameter(description = "추방 요청 데이터", required = true)
        @Valid @RequestBody request: KickParticipantRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        val requesterId = extractUserIdFromToken(authHeader)
        chatRoomService.kickParticipant(
            requesterId = requesterId,
            targetUserId = request.targetUserId,
            roomId = roomId
        )
        return ResponseEntity.ok(ApiResponse.success("참여자가 성공적으로 추방되었습니다."))
    }

    /**
     * 채팅방 정보를 업데이트합니다. (소유자만 가능)
     */
    @PutMapping("/{roomId}")
    @Operation(
        summary = "채팅방 정보 수정",
        description = "채팅방의 이름, 설명, 최대 참여자 수 등을 수정합니다. 소유자 권한이 필요합니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "200", description = "수정 성공"),
            SwaggerApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
            SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
            SwaggerApiResponse(responseCode = "403", description = "권한 없음 (소유자 권한 필요)"),
            SwaggerApiResponse(responseCode = "404", description = "채팅방을 찾을 수 없음")
        ]
    )
    suspend fun updateChatRoom(
        @Parameter(description = "JWT 인증 토큰", required = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authHeader: String,
        @Parameter(description = "수정할 채팅방 ID", required = true, example = "1")
        @PathVariable roomId: Long,
        @Parameter(description = "채팅방 수정 요청 데이터", required = true)
        @Valid @RequestBody request: UpdateChatRoomRequest
    ): ResponseEntity<ApiResponse<ChatRoomDto>> {
        val requesterId = extractUserIdFromToken(authHeader)
        val updatedRoom = chatRoomService.updateChatRoom(
            id = roomId,
            name = request.name,
            description = request.description,
            maxParticipants = request.maxParticipants,
            requesterId = requesterId
        )
        val count = userChatRoomService.countActiveParticipants(updatedRoom.id!!).awaitSingle()
        val updatedRoomDto = ChatRoomDto.from(updatedRoom, count.toInt(), true)
        return ResponseEntity.ok(ApiResponse.success(updatedRoomDto, "채팅방 정보가 성공적으로 업데이트되었습니다."))
    }

    /**
     * 채팅방을 삭제합니다. (소유자만 가능)
     */
    @DeleteMapping("/{roomId}")
    @Operation(
        summary = "채팅방 삭제",
        description = "채팅방을 완전히 삭제합니다. 소유자 권한이 필요하며, 모든 관련 데이터가 삭제됩니다."
    )
    @ApiResponses(
        value = [
            SwaggerApiResponse(responseCode = "204", description = "삭제 성공 (No Content)"),
            SwaggerApiResponse(responseCode = "401", description = "인증 실패"),
            SwaggerApiResponse(responseCode = "403", description = "권한 없음 (소유자 권한 필요)"),
            SwaggerApiResponse(responseCode = "404", description = "채팅방을 찾을 수 없음")
        ]
    )
    suspend fun deleteChatRoom(
        @Parameter(description = "JWT 인증 토큰", required = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authHeader: String,
        @Parameter(description = "삭제할 채팅방 ID", required = true, example = "1")
        @PathVariable roomId: Long
    ): ResponseEntity<ApiResponse<String>> {
        val userId = extractUserIdFromToken(authHeader)
        chatRoomService.deleteChatRoom(roomId, userId)
        return ResponseEntity.noContent().build()
    }

    // === Private Helper Methods ===

    /**
     * JWT 토큰에서 사용자 ID를 추출합니다.
     */
    private suspend fun extractUserIdFromToken(authHeader: String): Long {
        val token = jwtAuthenticationHelper.extractTokenFromHeader(authHeader)
            ?: throw IllegalArgumentException("유효하지 않은 인증 헤더입니다.")
        if (!jwtAuthenticationHelper.validateToken(token)) {
            throw IllegalArgumentException("유효하지 않은 토큰입니다.")
        }
        return jwtAuthenticationHelper.getUserIdFromToken(token)
    }
}
