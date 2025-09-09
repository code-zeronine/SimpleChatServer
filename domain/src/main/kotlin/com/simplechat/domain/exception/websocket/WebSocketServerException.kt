package com.simplechat.domain.exception.websocket

/**
 * WebSocket 서버 관련 예외입니다.
 */
class WebSocketServerException(
    message: String = WebSocketErrorCode.WS_SERVER_OVERLOADED.message,
    errorCode: WebSocketErrorCode = WebSocketErrorCode.WS_SERVER_OVERLOADED,
    cause: Throwable? = null
) : WebSocketException(errorCode, message, cause)
