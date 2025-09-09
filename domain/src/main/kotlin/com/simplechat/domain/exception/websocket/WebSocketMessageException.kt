package com.simplechat.domain.exception.websocket

/**
 * WebSocket 메시지 처리 관련 예외입니다.
 */
class WebSocketMessageException(
    message: String = WebSocketErrorCode.WS_MESSAGE_PARSING_FAILED.message,
    errorCode: WebSocketErrorCode = WebSocketErrorCode.WS_MESSAGE_PARSING_FAILED,
    cause: Throwable? = null
) : WebSocketException(errorCode, message, cause)
