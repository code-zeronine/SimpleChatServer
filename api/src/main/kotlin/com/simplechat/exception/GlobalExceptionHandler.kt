package com.simplechat.exception

import com.fasterxml.jackson.databind.ObjectMapper
import com.simplechat.domain.exception.auth.AuthenticationException
import com.simplechat.domain.exception.auth.AuthorizationException
import com.simplechat.domain.exception.business.BusinessLogicException
import com.simplechat.domain.exception.database.DatabaseException
import com.simplechat.domain.exception.ExternalServiceException
import com.simplechat.domain.exception.entity.ResourceNotFoundException
import com.simplechat.domain.exception.SimpleChatException
import com.simplechat.domain.exception.ValidationException
import com.simplechat.dto.ApiResponse
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

@Component
@Order(-2)
class GlobalExceptionHandler(
    private val objectMapper: ObjectMapper
) : ErrorWebExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    override fun handle(exchange: ServerWebExchange, ex: Throwable): Mono<Void> {
        val response = exchange.response
        
        logger.error("Unhandled exception occurred: ${ex.message}", ex)

        val (status, errorCode, message) = when (ex) {
            is ResourceNotFoundException -> Triple(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                ex.message ?: "Resource not found"
            )
            is AuthenticationException -> Triple(
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_FAILED",
                ex.message ?: "Authentication failed"
            )
            is AuthorizationException -> Triple(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                ex.message ?: "Access denied"
            )
            is ValidationException -> Triple(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                ex.message ?: "Validation failed"
            )
            is BusinessLogicException -> {
                // 특정 메시지에 대해 409 Conflict 반환
                when {
                    ex.message?.contains("참여하지 않은 채팅방") == true ||
                    ex.message?.contains("이미 참여") == true ||
                    ex.message?.contains("이미 존재") == true -> Triple(
                        HttpStatus.CONFLICT,
                        "CONFLICT",
                        ex.message ?: "Conflict error"
                    )
                    else -> Triple(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "BUSINESS_LOGIC_ERROR",
                        ex.message ?: "Business logic error"
                    )
                }
            }
            is ExternalServiceException -> Triple(
                HttpStatus.SERVICE_UNAVAILABLE,
                "EXTERNAL_SERVICE_UNAVAILABLE",
                ex.message ?: "External service unavailable"
            )
            is DatabaseException -> Triple(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "DATABASE_ERROR",
                "Database operation failed"
            )
            is IllegalArgumentException -> Triple(
                HttpStatus.BAD_REQUEST,
                "INVALID_PARAMETER",
                "Invalid request parameter"
            )
            is IllegalStateException -> Triple(
                HttpStatus.CONFLICT,
                "INVALID_STATE",
                "Invalid operation state"
            )
            is SecurityException -> Triple(
                HttpStatus.FORBIDDEN,
                "SECURITY_ERROR",
                "Access denied"
            )
            is NoSuchElementException -> Triple(
                HttpStatus.NOT_FOUND,
                "ELEMENT_NOT_FOUND",
                "Resource not found"
            )
            else -> Triple(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "Internal server error"
            )
        }

        // SimpleChatException인 경우 커스텀 에러 코드 사용
        val finalErrorCode = if (ex is SimpleChatException) ex.errorCode.code else errorCode

        // ApiResponse 구조로 에러 응답 생성
        val apiResponse = ApiResponse.error<Any>(
            code = finalErrorCode,
            message = message,
            path = exchange.request.path.value(),
            statusCode = status.value()
        )

        response.statusCode = status
        response.headers.add("Content-Type", MediaType.APPLICATION_JSON_VALUE)

        return try {
            val errorJson = objectMapper.writeValueAsString(apiResponse)
            val dataBuffer: DataBuffer = response.bufferFactory().wrap(errorJson.toByteArray(StandardCharsets.UTF_8))
            response.writeWith(Mono.just(dataBuffer))
        } catch (jsonError: Exception) {
            logger.error("Failed to serialize error response", jsonError)
            val fallbackJson = """{"success":false,"message":"Internal server error","timestamp":"${java.time.Instant.now()}"}"""
            val dataBuffer: DataBuffer = response.bufferFactory().wrap(fallbackJson.toByteArray(StandardCharsets.UTF_8))
            response.writeWith(Mono.just(dataBuffer))
        }
    }
}