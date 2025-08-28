package com.simplechat.infrastructure.service

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.simplechat.domain.entity.ChatMessage
import com.simplechat.domain.entity.MessageType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.LocalDateTime

/**
 * Redis 메시지 직렬화/역직렬화 서비스
 * 
 * 채팅 메시지와 시스템 메시지의 JSON 변환을 담당합니다.
 * 반응형 프로그래밍과 에러 핸들링을 지원합니다.
 */
@Service
class RedisMessageSerializationService(
    private val objectMapper: ObjectMapper
) {
    
    private val logger = LoggerFactory.getLogger(RedisMessageSerializationService::class.java)
    
    /**
     * ChatMessage 객체를 JSON 문자열로 직렬화합니다.
     * 
     * @param chatMessage 직렬화할 채팅 메시지
     * @return JSON 문자열을 담은 Mono
     */
    fun serializeChatMessage(chatMessage: ChatMessage): Mono<String> {
        return Mono.fromCallable {
                objectMapper.writeValueAsString(chatMessage)
            }
            .subscribeOn(Schedulers.boundedElastic())
            .doOnSuccess { json ->
                logger.trace("Serialized chat message: {} -> {}", chatMessage.id, json.length)
            }
            .doOnError { error ->
                logger.error("Failed to serialize chat message {}: {}", 
                    chatMessage.id, error.message, error)
            }
    }
    
    /**
     * JSON 문자열을 ChatMessage 객체로 역직렬화합니다.
     * 
     * @param jsonString 역직렬화할 JSON 문자열
     * @return ChatMessage 객체를 담은 Mono
     */
    fun deserializeChatMessage(jsonString: String): Mono<ChatMessage> {
        return Mono.fromCallable {
                objectMapper.readValue<ChatMessage>(jsonString)
            }
            .subscribeOn(Schedulers.boundedElastic())
            .doOnSuccess { chatMessage ->
                logger.trace("Deserialized chat message: {} -> {}", 
                    jsonString.length, chatMessage.id)
            }
            .doOnError { error ->
                logger.error("Failed to deserialize chat message from JSON: {}", 
                    error.message, error)
            }
    }
    
    /**
     * 일반 객체를 JSON 문자열로 직렬화합니다.
     * 
     * @param obj 직렬화할 객체
     * @return JSON 문자열을 담은 Mono
     */
    fun serializeObject(obj: Any): Mono<String> {
        return Mono.fromCallable {
                objectMapper.writeValueAsString(obj)
            }
            .subscribeOn(Schedulers.boundedElastic())
            .doOnSuccess { json ->
                logger.trace("Serialized object of type {}: {} chars", 
                    obj::class.simpleName, json.length)
            }
            .doOnError { error ->
                logger.error("Failed to serialize object of type {}: {}", 
                    obj::class.simpleName, error.message, error)
            }
    }
    
    /**
     * JSON 문자열을 Map 객체로 역직렬화합니다.
     * 
     * @param jsonString 역직렬화할 JSON 문자열
     * @return Map 객체를 담은 Mono
     */
    fun deserializeToMap(jsonString: String): Mono<Map<String, Any>> {
        return Mono.fromCallable {
                objectMapper.readValue<Map<String, Any>>(jsonString)
            }
            .subscribeOn(Schedulers.boundedElastic())
            .doOnSuccess { map ->
                logger.trace("Deserialized JSON to map with {} keys", map.size)
            }
            .doOnError { error ->
                logger.error("Failed to deserialize JSON to map: {}", error.message, error)
            }
    }
    
    /**
     * 시스템 메시지 객체를 생성하고 직렬화합니다.
     * 
     * @param message 시스템 메시지 내용
     * @param additionalData 추가 데이터 (선택적)
     * @return JSON 문자열을 담은 Mono
     */
    fun createSystemMessage(
        message: String,
        additionalData: Map<String, Any>? = null
    ): Mono<String> {
        return Mono.fromCallable {
                val systemMessage = mutableMapOf<String, Any>(
                    "type" to "SYSTEM",
                    "message" to message,
                    "timestamp" to System.currentTimeMillis()
                )
                
                additionalData?.let { data ->
                    systemMessage.putAll(data)
                }
                
                objectMapper.writeValueAsString(systemMessage)
            }
            .subscribeOn(Schedulers.boundedElastic())
            .doOnSuccess { json ->
                logger.debug("Created system message: {}", message)
            }
            .doOnError { error ->
                logger.error("Failed to create system message: {}", error.message, error)
            }
    }
    
    /**
     * 사용자 알림 메시지를 생성하고 직렬화합니다.
     * 
     * @param userId 대상 사용자 ID
     * @param message 알림 내용
     * @param notificationType 알림 타입
     * @return JSON 문자열을 담은 Mono
     */
    fun createUserNotification(
        userId: Long,
        message: String,
        notificationType: String = "INFO"
    ): Mono<String> {
        return Mono.fromCallable {
                val notification = mapOf(
                    "type" to "USER_NOTIFICATION",
                    "userId" to userId,
                    "message" to message,
                    "notificationType" to notificationType,
                    "timestamp" to System.currentTimeMillis()
                )
                
                objectMapper.writeValueAsString(notification)
            }
            .subscribeOn(Schedulers.boundedElastic())
            .doOnSuccess { json ->
                logger.debug("Created user notification for user {}: {}", userId, message)
            }
            .doOnError { error ->
                logger.error("Failed to create user notification: {}", error.message, error)
            }
    }
    
    /**
     * 채팅방 이벤트 메시지를 생성하고 직렬화합니다.
     * 
     * @param roomId 채팅방 ID
     * @param eventType 이벤트 타입 (JOIN, LEAVE, etc.)
     * @param userId 사용자 ID
     * @param additionalData 추가 데이터
     * @return JSON 문자열을 담은 Mono
     */
    fun createRoomEvent(
        roomId: Long,
        eventType: String,
        userId: Long,
        additionalData: Map<String, Any>? = null
    ): Mono<String> {
        return Mono.fromCallable {
                val roomEvent = mutableMapOf<String, Any>(
                    "type" to "ROOM_EVENT",
                    "roomId" to roomId,
                    "eventType" to eventType,
                    "userId" to userId,
                    "timestamp" to System.currentTimeMillis()
                )
                
                additionalData?.let { data ->
                    roomEvent.putAll(data)
                }
                
                objectMapper.writeValueAsString(roomEvent)
            }
            .subscribeOn(Schedulers.boundedElastic())
            .doOnSuccess { json ->
                logger.debug("Created room event for room {}: {} by user {}", 
                    roomId, eventType, userId)
            }
            .doOnError { error ->
                logger.error("Failed to create room event: {}", error.message, error)
            }
    }
    
    /**
     * JSON 문자열이 유효한 ChatMessage인지 검증합니다.
     * 
     * @param jsonString 검증할 JSON 문자열
     * @return 유효성 검증 결과를 담은 Mono
     */
    fun validateChatMessageJson(jsonString: String): Mono<Boolean> {
        return deserializeChatMessage(jsonString)
            .map { chatMessage ->
                chatMessage.isValid()
            }
            .onErrorReturn(false)
            .doOnNext { isValid ->
                logger.trace("Chat message JSON validation result: {}", isValid)
            }
    }
    
    /**
     * 메시지 타입을 기반으로 적절한 역직렬화를 수행합니다.
     * 
     * @param jsonString 역직렬화할 JSON 문자열
     * @return 역직렬화된 객체를 담은 Mono
     */
    fun deserializeByType(jsonString: String): Mono<Any> {
        return deserializeToMap(jsonString)
            .map { messageMap ->
                when (messageMap["type"]) {
                    "TEXT", "JOIN", "LEAVE" -> {
                        // ChatMessage로 역직렬화 시도
                        runCatching {
                            objectMapper.convertValue(messageMap, ChatMessage::class.java)
                        }.getOrElse {
                            // 변환 실패 시 로그 기록 후 원본 반환
                            logger.error("Failed to convert message to ChatMessage: {}", it.message, it)
                            messageMap
                        }
                    }
                    "SYSTEM", "USER_NOTIFICATION", "ROOM_EVENT" -> messageMap
                    else -> messageMap
                }
            }
            .doOnSuccess { obj ->
                logger.trace("Deserialized message by type: {}", obj::class.simpleName)
            }
            .doOnError { error ->
                logger.error("Failed to deserialize message by type: {}", error.message, error)
            }
    }
    
    /**
     * 에러 메시지를 생성하고 직렬화합니다.
     * 
     * @param error 에러 내용
     * @param context 에러 컨텍스트 정보
     * @return JSON 문자열을 담은 Mono
     */
    fun createErrorMessage(error: String, context: Map<String, Any>? = null): Mono<String> {
        return Mono.fromCallable {
                val errorMessage = mutableMapOf<String, Any>(
                    "type" to "ERROR",
                    "error" to error,
                    "timestamp" to System.currentTimeMillis()
                )
                
                context?.let { ctx ->
                    errorMessage["context"] = ctx
                }
                
                objectMapper.writeValueAsString(errorMessage)
            }
            .subscribeOn(Schedulers.boundedElastic())
            .doOnSuccess { json ->
                logger.debug("Created error message: {}", error)
            }
            .doOnError { serializationError ->
                logger.error("Failed to create error message: {}", serializationError.message)
            }
    }
}