package com.simplechat.domain.exception.websocket

/**
 * WebSocket 권한 관련 예외입니다.
 */
class WebSocketAuthorizationException(
    message: String = WebSocketErrorCode.WS_AUTHORIZATION_FAILED.message,
    errorCode: WebSocketErrorCode = WebSocketErrorCode.WS_AUTHORIZATION_FAILED,
    cause: Throwable? = null
) : WebSocketException(errorCode, message, cause)
