package com.simplechat.domain.exception.websocket

/**
 * WebSocket 채팅방 관련 예외입니다.
 */
class WebSocketChatRoomException(
    message: String = WebSocketErrorCode.WS_CHATROOM_NOT_FOUND.message,
    errorCode: WebSocketErrorCode = WebSocketErrorCode.WS_CHATROOM_NOT_FOUND,
    cause: Throwable? = null
) : WebSocketException(errorCode, message, cause)
