package com.simplechat.controller

import com.simplechat.dto.ApiResponse
import com.simplechat.dto.MessageDto
import com.simplechat.service.MessageService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/messages")
class MessageController(
    private val messageService: MessageService
) {

    @GetMapping("/room/{roomId}")
    fun getMessagesByRoom(
        @PathVariable roomId: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int
    ): Flux<MessageDto> {
        require(roomId.isNotBlank()) { "roomId must not be blank" }
        require(page >= 0) { "page must be greater than or equal to 0" }
        require(size > 0) { "size must be greater than 0" }
        return messageService.getMessagesByRoom(roomId, page, size)
    }

    @GetMapping("/room/{roomId}/recent")
    fun getRecentMessages(
        @PathVariable roomId: String,
        @RequestParam(defaultValue = "10") size: Int
    ): Flux<MessageDto> {
        require(roomId.isNotBlank()) { "roomId must not be blank" }
        require(size > 0) { "size must be greater than 0" }
        return messageService.getRecentMessages(roomId, size)
    }

    @GetMapping("/room/{roomId}/count")
    fun countMessages(@PathVariable roomId: String): Mono<ApiResponse<Map<String, Long>>> {
        require(roomId.isNotBlank()) { "roomId must not be blank" }
        return messageService.countMessages(roomId)
            .map { ApiResponse.success(mapOf("count" to it)) }
    }
}
