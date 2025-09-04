package com.simplechat.controller

import com.simplechat.domain.entity.ChatRoomRole
import com.simplechat.domain.repository.ChatRoomRepository
import com.simplechat.domain.repository.UserRepository
import com.simplechat.dto.ApiResponse
import com.simplechat.dto.ChangeParticipantRoleRequest
import com.simplechat.dto.ChatRoomDetailsDto
import com.simplechat.dto.ChatRoomDto
import com.simplechat.dto.ChatRoomListResponse
import com.simplechat.dto.CreateChatRoomRequest
import com.simplechat.dto.KickParticipantRequest
import com.simplechat.dto.ParticipantDto
import com.simplechat.dto.UpdateChatRoomRequest
import com.simplechat.infrastructure.service.WebSocketSessionManager
import com.simplechat.security.JwtAuthenticationHelper
import com.simplechat.service.ChatRoomService
import com.simplechat.service.UserChatRoomService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
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
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
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
    fun createChatRoom(
        @Parameter(description = "JWT 인증 토큰", required = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authHeader: String,
        @Parameter(description = "채팅방 생성 요청 데이터", required = true)
        @Valid @RequestBody request: CreateChatRoomRequest
    ): Mono<ResponseEntity<ApiResponse<ChatRoomDto>>> {
        return extractUserIdFromToken(authHeader)
            .flatMap { userId ->
                chatRoomService.createChatRoom(
                    name = request.name,
                    description = request.description,
                    isPrivate = request.isPrivate,
                    maxParticipants = request.maxParticipants,
                    ownerId = userId
                )
            }
            .flatMap { chatRoom ->
                userChatRoomService.countActiveParticipants(chatRoom.id!!)
                    .map { participantCount ->
                        // Creator is always joined
                        ChatRoomDto.from(chatRoom, participantCount.toInt(), true)
                    }
            }
            .map { chatRoomDto ->
                ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(ApiResponse.success(chatRoomDto, "채팅방이 성공적으로 생성되었습니다."))
            }
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
    fun getChatRooms(
        @Parameter(description = "JWT 인증 토큰", required = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authHeader: String,
        @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기", example = "20")
        @RequestParam(defaultValue = "20") size: Int,
        @Parameter(description = "필터 (all, joined)", example = "all")
        @RequestParam(defaultValue = "all") filter: String
    ): Mono<ResponseEntity<ApiResponse<ChatRoomListResponse>>> {
        return extractUserIdFromToken(authHeader)
            .flatMap { userId ->
                val roomsFlux = when (filter) {
                    "joined" -> userChatRoomService.getUserActiveRooms(userId)
                        .flatMap { userChatRoom -> chatRoomService.findChatRoomById(userChatRoom.chatRoomId) }
                    else -> chatRoomService.getPublicRooms(size * (page + 1)) // Fetch all public rooms with pagination
                }

                val roomsWithDetailsFlux = roomsFlux.flatMap { room ->
                    val countMono = userChatRoomService.countActiveParticipants(room.id!!)
                    val isJoinedMono = userChatRoomService.isActiveParticipant(userId, room.id!!)
                    Mono.zip(countMono, isJoinedMono)
                        .map { tuple ->
                            val count = tuple.t1
                            val isJoined = tuple.t2
                            ChatRoomDto.from(room, count.toInt(), isJoined)
                        }
                }

                val roomsMono = roomsWithDetailsFlux.skip((page * size).toLong()).take(size.toLong()).collectList()
                
                val totalCountMono = when (filter) {
                    "joined" -> userChatRoomService.countUserActiveRooms(userId)
                    else -> chatRoomRepository.count() // Note: This should ideally be countPublicRooms()
                }

                Mono.zip(roomsMono, totalCountMono)
            }
            .map { tuple ->
                val rooms = tuple.t1
                val totalCount = tuple.t2
                val hasNext = (page + 1) * size < totalCount

                val response = ChatRoomListResponse(
                    rooms = rooms,
                    totalCount = totalCount.toInt(),
                    page = page,
                    size = size,
                    hasNext = hasNext
                )
                ResponseEntity.ok(ApiResponse.success(response))
            }
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
    fun getChatRoomDetails(
        @Parameter(description = "JWT 인증 토큰", required = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authHeader: String,
        @Parameter(description = "채팅방 ID", required = true, example = "1")
        @PathVariable roomId: Long
    ): Mono<ResponseEntity<ApiResponse<ChatRoomDetailsDto>>> {
        return extractUserIdFromToken(authHeader)
            .flatMap { userId ->
                chatRoomService.getChatRoomDetails(roomId, userId)
                    .flatMap { details ->
                        val isJoined = details.userRelationship != null && details.userRelationship.isActive
                        val roomDto = ChatRoomDto.from(details.room, details.participantCount, isJoined)

                        val participantsFlux = Flux.fromIterable(details.participants)
                            .flatMap { ucr ->
                                val userMono = userRepository.findById(ucr.userId)
                                val isOnlineMono = Mono.fromCallable { webSocketSessionManager.getUserActiveSessionCount(ucr.userId) > 0 }
                                Mono.zip(userMono, isOnlineMono)
                                    .map { tuple -> ParticipantDto.from(ucr, tuple.t1.nickname, tuple.t2) }
                            }

                        val ownerMono = userRepository.findById(details.room.createdBy)
                            .flatMap { user ->
                                val ownerRelationship = details.participants.find { it.userId == user.id }
                                val defaultRelationship = details.userRelationship ?: ownerRelationship!!
                                val isOnlineMono = Mono.fromCallable { webSocketSessionManager.getUserActiveSessionCount(user.id!!) > 0 }
                                isOnlineMono.map { isOnline ->
                                    ParticipantDto.from(ownerRelationship ?: defaultRelationship, user.nickname, isOnline)
                                }
                            }

                        Mono.zip(
                            participantsFlux.collectList(),
                            participantsFlux.filter { it.role == ChatRoomRole.ADMIN }.collectList(),
                            ownerMono
                        ).map { tuple ->
                            ChatRoomDetailsDto(
                                room = roomDto,
                                participants = tuple.t1,
                                admins = tuple.t2,
                                owner = tuple.t3
                            )
                        }
                    }
            }
            .map { detailsDto -> ResponseEntity.ok(ApiResponse.success(detailsDto)) }
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
    fun joinChatRoom(
        @Parameter(description = "JWT 인증 토큰", required = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authHeader: String,
        @Parameter(description = "참여할 채팅방 ID", required = true, example = "1")
        @PathVariable roomId: Long
    ): Mono<ResponseEntity<ApiResponse<Unit>>> {
        return extractUserIdFromToken(authHeader)
            .flatMap { userId ->
                chatRoomService.joinChatRoom(userId, roomId)
            }
            .map {
                ResponseEntity.ok(ApiResponse.success("채팅방에 성공적으로 참여했습니다."))
            }
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
    fun leaveChatRoom(
        @Parameter(description = "JWT 인증 토큰", required = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authHeader: String,
        @Parameter(description = "퇴장할 채팅방 ID", required = true, example = "1")
        @PathVariable roomId: Long
    ): Mono<ResponseEntity<ApiResponse<Unit>>> {
        return extractUserIdFromToken(authHeader)
            .flatMap { userId ->
                chatRoomService.leaveChatRoom(userId, roomId)
            }
            .map {
                ResponseEntity.ok(ApiResponse.success("채팅방에서 성공적으로 퇴장했습니다."))
            }
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
    fun getChatRoomParticipants(
        @Parameter(description = "JWT 인증 토큰", required = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authHeader: String,
        @Parameter(description = "조회할 채팅방 ID", required = true, example = "1")
        @PathVariable roomId: Long
    ): Mono<ResponseEntity<ApiResponse<List<ParticipantDto>>>> {
        return extractUserIdFromToken(authHeader)
            .flatMapMany { userId ->
                chatRoomService.getRoomParticipants(roomId, userId)
                    .flatMap { userChatRoom ->
                        val userMono = userRepository.findById(userChatRoom.userId)
                        val isOnlineMono = Mono.fromCallable { 
                            webSocketSessionManager.getUserActiveSessionCount(userChatRoom.userId) > 0
                        }
                        
                        Mono.zip(userMono, isOnlineMono)
                            .map { tuple -> 
                                val user = tuple.t1
                                val isOnline = tuple.t2
                                ParticipantDto.from(userChatRoom, user.nickname, isOnline)
                            }
                    }
            }
            .collectList()
            .map { participants ->
                ResponseEntity.ok(ApiResponse.success(participants))
            }
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
    fun changeParticipantRole(
        @Parameter(description = "JWT 인증 토큰", required = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authHeader: String,
        @Parameter(description = "채팅방 ID", required = true, example = "1")
        @PathVariable roomId: Long,
        @Parameter(description = "역할 변경 요청 데이터", required = true)
        @Valid @RequestBody request: ChangeParticipantRoleRequest
    ): Mono<ResponseEntity<ApiResponse<Unit>>> {
        return extractUserIdFromToken(authHeader)
            .flatMap { requesterId ->
                chatRoomService.changeParticipantRole(
                    requesterId = requesterId,
                    targetUserId = request.targetUserId,
                    roomId = roomId,
                    newRole = request.newRole
                )
            }
            .map {
                ResponseEntity.ok(ApiResponse.success("참여자 역할이 성공적으로 변경되었습니다."))
            }
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
    fun kickParticipant(
        @Parameter(description = "JWT 인증 토큰", required = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authHeader: String,
        @Parameter(description = "채팅방 ID", required = true, example = "1")
        @PathVariable roomId: Long,
        @Parameter(description = "추방 요청 데이터", required = true)
        @Valid @RequestBody request: KickParticipantRequest
    ): Mono<ResponseEntity<ApiResponse<Unit>>> {
        return extractUserIdFromToken(authHeader)
            .flatMap { requesterId ->
                chatRoomService.kickParticipant(
                    requesterId = requesterId,
                    targetUserId = request.targetUserId,
                    roomId = roomId
                )
            }
            .map {
                ResponseEntity.ok(ApiResponse.success("참여자가 성공적으로 추방되었습니다."))
            }
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
    fun updateChatRoom(
        @Parameter(description = "JWT 인증 토큰", required = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authHeader: String,
        @Parameter(description = "수정할 채팅방 ID", required = true, example = "1")
        @PathVariable roomId: Long,
        @Parameter(description = "채팅방 수정 요청 데이터", required = true)
        @Valid @RequestBody request: UpdateChatRoomRequest
    ): Mono<ResponseEntity<ApiResponse<ChatRoomDto>>> {
        return extractUserIdFromToken(authHeader)
            .flatMap { requesterId ->
                chatRoomService.updateChatRoom(
                    id = roomId,
                    name = request.name,
                    description = request.description,
                    maxParticipants = request.maxParticipants,
                    requesterId = requesterId
                )
            }
            .flatMap { updatedRoom ->
                userChatRoomService.countActiveParticipants(updatedRoom.id!!)
                    .map { count -> 
                        // Assume requester is joined
                        ChatRoomDto.from(updatedRoom, count.toInt(), true) 
                    }
            }
            .map { updatedRoomDto ->
                ResponseEntity.ok(ApiResponse.success(updatedRoomDto, "채팅방 정보가 성공적으로 업데이트되었습니다."))
            }
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
    fun deleteChatRoom(
        @Parameter(description = "JWT 인증 토큰", required = true)
        @RequestHeader(HttpHeaders.AUTHORIZATION) authHeader: String,
        @Parameter(description = "삭제할 채팅방 ID", required = true, example = "1")
        @PathVariable roomId: Long
    ): Mono<ResponseEntity<ApiResponse<String>>> {
        return extractUserIdFromToken(authHeader)
            .flatMap { userId ->
                chatRoomService.deleteChatRoom(roomId, userId)
            }
            .map {
                ResponseEntity.noContent().build()
            }
    }

    // === Private Helper Methods ===

    /**
     * JWT 토큰에서 사용자 ID를 추출합니다.
     */
    private fun extractUserIdFromToken(authHeader: String): Mono<Long> {
        return jwtAuthenticationHelper.extractTokenFromHeader(authHeader)
            .flatMap { token ->
                jwtAuthenticationHelper.validateToken(token)
                    .then(jwtAuthenticationHelper.getUserIdFromToken(token))
            }
    }
}
