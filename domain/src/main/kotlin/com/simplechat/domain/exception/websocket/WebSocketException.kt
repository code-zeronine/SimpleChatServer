package com.simplechat.domain.exception.websocket

/**
 * WebSocket 관련 모든 예외의 기본 클래스입니다.
 * 표준화된 WebSocket 에러 코드와 메시지를 포함합니다.
 */
abstract class WebSocketException(
    val errorCode: WebSocketErrorCode,
    message: String = errorCode.message,
    cause: Throwable? = null
) : RuntimeException(message, cause) {
    
    /**
     * 에러를 JSON 형태로 직렬화할 수 있는 맵으로 변환합니다.
     */
    fun toErrorMap(): Map<String, Any> {
        return mapOf(
            "type" to "ERROR",
            "errorCode" to errorCode.code,
            "message" to (message ?: "Unknown error"),
            "timestamp" to System.currentTimeMillis()
        )
    }
}
