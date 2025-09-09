package com.simplechat.domain.exception.entity

import com.simplechat.domain.exception.ErrorCode

/**
 * 사용자를 찾을 수 없을 때 발생하는 예외입니다.
 */
class UserNotFoundException(
    message: String = ErrorCode.USER_NOT_FOUND.message,
    errorCode: ErrorCode = ErrorCode.USER_NOT_FOUND,
    cause: Throwable? = null
) : ResourceNotFoundException(message, errorCode, cause)
