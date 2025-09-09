package com.simplechat.domain.exception.websocket

/**
 * WebSocket 인증 관련 예외입니다.
 */
class WebSocketAuthenticationException(
    message: String = WebSocketErrorCode.WS_AUTHENTICATION_FAILED.message,
    errorCode: WebSocketErrorCode = WebSocketErrorCode.WS_AUTHENTICATION_FAILED,
    cause: Throwable? = null
) : WebSocketException(errorCode, message, cause)
