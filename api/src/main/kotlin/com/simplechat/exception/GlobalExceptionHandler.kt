package com.simplechat.exception

import com.simplechat.domain.exception.*
import org.slf4j.LoggerFactory
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler
import org.springframework.core.annotation.Order
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets
import java.time.Instant

@Component
@Order(-2)
class GlobalExceptionHandler : ErrorWebExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    override fun handle(exchange: ServerWebExchange, ex: Throwable): Mono<Void> {
        val response = exchange.response
        
        logger.error("Unhandled exception occurred: ${ex.message}", ex)

        val (status, message) = when (ex) {
            is ResourceNotFoundException -> HttpStatus.NOT_FOUND to (ex.message ?: "Resource not found")
            is AuthenticationException -> HttpStatus.UNAUTHORIZED to (ex.message ?: "Authentication failed")
            is AuthorizationException -> HttpStatus.FORBIDDEN to (ex.message ?: "Access denied")
            is ValidationException -> HttpStatus.BAD_REQUEST to (ex.message ?: "Validation failed")
            is BusinessLogicException -> HttpStatus.UNPROCESSABLE_ENTITY to (ex.message ?: "Business logic error")
            is ExternalServiceException -> HttpStatus.SERVICE_UNAVAILABLE to (ex.message ?: "External service unavailable")
            is DatabaseException -> HttpStatus.INTERNAL_SERVER_ERROR to "Database operation failed"
            is IllegalArgumentException -> HttpStatus.BAD_REQUEST to "Invalid request parameter"
            is IllegalStateException -> HttpStatus.CONFLICT to "Invalid operation state"
            is SecurityException -> HttpStatus.FORBIDDEN to "Access denied"
            is NoSuchElementException -> HttpStatus.NOT_FOUND to "Resource not found"
            else -> HttpStatus.INTERNAL_SERVER_ERROR to "Internal server error"
        }

        response.statusCode = status
        response.headers.add("Content-Type", MediaType.APPLICATION_JSON_VALUE)

        val errorResponse = mutableMapOf(
            "timestamp" to Instant.now().toString(),
            "status" to status.value(),
            "error" to status.reasonPhrase,
            "message" to message,
            "path" to exchange.request.path.value()
        )
        if (ex is SimpleChatException) {
            errorResponse["code"] = ex.errorCode.code
        }

        val errorJson = buildString {
            append("{")
            errorResponse.entries.joinToString(",") { (key, value) ->
                "\"$key\":\"$value\""
            }.let { append(it) }
            append("}")
        }

        val dataBuffer: DataBuffer = response.bufferFactory().wrap(errorJson.toByteArray(StandardCharsets.UTF_8))
        
        return response.writeWith(Mono.just(dataBuffer))
    }
}