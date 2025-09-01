package com.simplechat.domain.exception

/**
 * WebSocket 특화 에러 코드를 정의합니다.
 */
enum class WebSocketErrorCode(val code: String, val message: String) {
    // Connection Errors
    WS_CONNECTION_FAILED("WS-001", "WebSocket 연결에 실패했습니다."),
    WS_CONNECTION_LIMIT_EXCEEDED("WS-002", "WebSocket 연결 수 제한을 초과했습니다."),
    WS_CONNECTION_TIMEOUT("WS-003", "WebSocket 연결 시간이 초과되었습니다."),
    WS_CONNECTION_CLOSED("WS-004", "WebSocket 연결이 종료되었습니다."),
    WS_CONNECTION_INTERRUPTED("WS-005", "WebSocket 연결이 중단되었습니다."),
    
    // Authentication & Authorization Errors
    WS_AUTHENTICATION_FAILED("WS-100", "WebSocket 인증에 실패했습니다."),
    WS_AUTHORIZATION_FAILED("WS-101", "WebSocket 권한 확인에 실패했습니다."),
    WS_USER_ID_EXTRACTION_FAILED("WS-102", "사용자 ID 추출에 실패했습니다."),
    WS_TOKEN_VALIDATION_FAILED("WS-103", "WebSocket 토큰 검증에 실패했습니다."),
    WS_INVALID_CREDENTIALS("WS-104", "WebSocket 인증 정보가 유효하지 않습니다."),
    
    // Message Processing Errors
    WS_MESSAGE_PARSING_FAILED("WS-200", "WebSocket 메시지 파싱에 실패했습니다."),
    WS_MESSAGE_VALIDATION_FAILED("WS-201", "WebSocket 메시지 유효성 검사에 실패했습니다."),
    WS_MESSAGE_TOO_LARGE("WS-202", "WebSocket 메시지 크기가 제한을 초과했습니다."),
    WS_MESSAGE_MALFORMED("WS-203", "WebSocket 메시지 형식이 올바르지 않습니다."),
    WS_MESSAGE_TYPE_UNSUPPORTED("WS-204", "지원되지 않는 WebSocket 메시지 타입입니다."),
    WS_MESSAGE_DELIVERY_FAILED("WS-205", "WebSocket 메시지 전송에 실패했습니다."),
    
    // Chat Room Errors
    WS_CHATROOM_NOT_FOUND("WS-300", "채팅방을 찾을 수 없습니다."),
    WS_CHATROOM_ACCESS_DENIED("WS-301", "채팅방에 접근할 권한이 없습니다."),
    WS_CHATROOM_FULL("WS-302", "채팅방이 가득 차 있습니다."),
    WS_USER_NOT_IN_CHATROOM("WS-303", "사용자가 해당 채팅방에 참여하고 있지 않습니다."),
    WS_CHATROOM_JOIN_FAILED("WS-304", "채팅방 참여에 실패했습니다."),
    WS_CHATROOM_LEAVE_FAILED("WS-305", "채팅방 퇴장에 실패했습니다."),
    
    // Rate Limiting Errors
    WS_RATE_LIMIT_EXCEEDED("WS-400", "WebSocket 메시지 전송 제한을 초과했습니다."),
    WS_BURST_LIMIT_EXCEEDED("WS-401", "WebSocket 버스트 제한을 초과했습니다."),
    WS_THROTTLING_ACTIVE("WS-402", "WebSocket 메시지 전송이 제한되었습니다."),
    
    // Session Management Errors
    WS_SESSION_NOT_FOUND("WS-500", "WebSocket 세션을 찾을 수 없습니다."),
    WS_SESSION_EXPIRED("WS-501", "WebSocket 세션이 만료되었습니다."),
    WS_SESSION_INVALID("WS-502", "WebSocket 세션이 유효하지 않습니다."),
    WS_SESSION_CONFLICT("WS-503", "WebSocket 세션 충돌이 발생했습니다."),
    
    // Server Errors
    WS_SERVER_OVERLOADED("WS-600", "WebSocket 서버가 과부하 상태입니다."),
    WS_SERVICE_UNAVAILABLE("WS-601", "WebSocket 서비스를 사용할 수 없습니다."),
    WS_INTERNAL_ERROR("WS-602", "WebSocket 내부 오류가 발생했습니다."),
    WS_CONFIGURATION_ERROR("WS-603", "WebSocket 설정 오류가 발생했습니다."),
    
    // Generic
    WS_UNKNOWN_ERROR("WS-999", "알 수 없는 WebSocket 오류가 발생했습니다.")
}

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

/**
 * WebSocket 연결 관련 예외입니다.
 */
class WebSocketConnectionException(
    message: String = WebSocketErrorCode.WS_CONNECTION_FAILED.message,
    errorCode: WebSocketErrorCode = WebSocketErrorCode.WS_CONNECTION_FAILED,
    cause: Throwable? = null
) : WebSocketException(errorCode, message, cause)

/**
 * WebSocket 인증 관련 예외입니다.
 */
class WebSocketAuthenticationException(
    message: String = WebSocketErrorCode.WS_AUTHENTICATION_FAILED.message,
    errorCode: WebSocketErrorCode = WebSocketErrorCode.WS_AUTHENTICATION_FAILED,
    cause: Throwable? = null
) : WebSocketException(errorCode, message, cause)

/**
 * WebSocket 권한 관련 예외입니다.
 */
class WebSocketAuthorizationException(
    message: String = WebSocketErrorCode.WS_AUTHORIZATION_FAILED.message,
    errorCode: WebSocketErrorCode = WebSocketErrorCode.WS_AUTHORIZATION_FAILED,
    cause: Throwable? = null
) : WebSocketException(errorCode, message, cause)

/**
 * WebSocket 메시지 처리 관련 예외입니다.
 */
class WebSocketMessageException(
    message: String = WebSocketErrorCode.WS_MESSAGE_PARSING_FAILED.message,
    errorCode: WebSocketErrorCode = WebSocketErrorCode.WS_MESSAGE_PARSING_FAILED,
    cause: Throwable? = null
) : WebSocketException(errorCode, message, cause)

/**
 * WebSocket 채팅방 관련 예외입니다.
 */
class WebSocketChatRoomException(
    message: String = WebSocketErrorCode.WS_CHATROOM_NOT_FOUND.message,
    errorCode: WebSocketErrorCode = WebSocketErrorCode.WS_CHATROOM_NOT_FOUND,
    cause: Throwable? = null
) : WebSocketException(errorCode, message, cause)

/**
 * WebSocket 속도 제한 관련 예외입니다.
 */
class WebSocketRateLimitException(
    message: String = WebSocketErrorCode.WS_RATE_LIMIT_EXCEEDED.message,
    errorCode: WebSocketErrorCode = WebSocketErrorCode.WS_RATE_LIMIT_EXCEEDED,
    cause: Throwable? = null
) : WebSocketException(errorCode, message, cause)

/**
 * WebSocket 세션 관련 예외입니다.
 */
class WebSocketSessionException(
    message: String = WebSocketErrorCode.WS_SESSION_NOT_FOUND.message,
    errorCode: WebSocketErrorCode = WebSocketErrorCode.WS_SESSION_NOT_FOUND,
    cause: Throwable? = null
) : WebSocketException(errorCode, message, cause)

/**
 * WebSocket 서버 관련 예외입니다.
 */
class WebSocketServerException(
    message: String = WebSocketErrorCode.WS_SERVER_OVERLOADED.message,
    errorCode: WebSocketErrorCode = WebSocketErrorCode.WS_SERVER_OVERLOADED,
    cause: Throwable? = null
) : WebSocketException(errorCode, message, cause)