package com.simplechat.domain.exception.websocket

/**
 * WebSocket 연결 관련 예외입니다.
 */
class WebSocketConnectionException(
    message: String = WebSocketErrorCode.WS_CONNECTION_FAILED.message,
    errorCode: WebSocketErrorCode = WebSocketErrorCode.WS_CONNECTION_FAILED,
    cause: Throwable? = null
) : WebSocketException(errorCode, message, cause)
