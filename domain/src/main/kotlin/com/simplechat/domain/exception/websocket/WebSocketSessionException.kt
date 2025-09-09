package com.simplechat.domain.exception.websocket

/**
 * WebSocket 세션 관련 예외입니다.
 */
class WebSocketSessionException(
    message: String = WebSocketErrorCode.WS_SESSION_NOT_FOUND.message,
    errorCode: WebSocketErrorCode = WebSocketErrorCode.WS_SESSION_NOT_FOUND,
    cause: Throwable? = null
) : WebSocketException(errorCode, message, cause)
