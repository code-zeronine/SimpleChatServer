package com.simplechat.domain.exception.entity

import com.simplechat.domain.exception.ErrorCode

/**
 * 채팅방을 찾을 수 없을 때 발생하는 예외입니다.
 */
class ChatRoomNotFoundException(
    message: String = ErrorCode.CHAT_ROOM_NOT_FOUND.message,
    errorCode: ErrorCode = ErrorCode.CHAT_ROOM_NOT_FOUND,
    cause: Throwable? = null
) : ResourceNotFoundException(message, errorCode, cause)
