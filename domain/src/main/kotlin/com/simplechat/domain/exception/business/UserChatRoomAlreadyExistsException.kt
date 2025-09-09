package com.simplechat.domain.exception.business

import com.simplechat.domain.exception.ErrorCode

/**
 * 사용자-채팅방 관계가 이미 존재할 때 발생하는 예외입니다.
 */
class UserChatRoomAlreadyExistsException(
    message: String = ErrorCode.USER_ALREADY_IN_CHAT_ROOM.message,
    errorCode: ErrorCode = ErrorCode.USER_ALREADY_IN_CHAT_ROOM,
    cause: Throwable? = null
) : BusinessLogicException(message, errorCode, cause)
