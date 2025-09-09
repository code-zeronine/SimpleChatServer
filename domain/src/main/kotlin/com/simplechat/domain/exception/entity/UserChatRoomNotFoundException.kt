package com.simplechat.domain.exception.entity

import com.simplechat.domain.exception.ErrorCode

/**
 * 사용자-채팅방 관계를 찾을 수 없을 때 발생하는 예외입니다.
 */
class UserChatRoomNotFoundException(
    message: String = ErrorCode.USER_CHAT_ROOM_NOT_FOUND.message,
    errorCode: ErrorCode = ErrorCode.USER_CHAT_ROOM_NOT_FOUND,
    cause: Throwable? = null
) : ResourceNotFoundException(message, errorCode, cause)
