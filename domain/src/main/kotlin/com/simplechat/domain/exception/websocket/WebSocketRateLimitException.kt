package com.simplechat.domain.exception.websocket

/**
 * WebSocket 속도 제한 관련 예외입니다.
 */
class WebSocketRateLimitException(
    message: String = WebSocketErrorCode.WS_RATE_LIMIT_EXCEEDED.message,
    errorCode: WebSocketErrorCode = WebSocketErrorCode.WS_RATE_LIMIT_EXCEEDED,
    cause: Throwable? = null
) : WebSocketException(errorCode, message, cause)
