package com.simplechat.domain.exception.auth

import com.simplechat.domain.exception.ErrorCode
import com.simplechat.domain.exception.SimpleChatException

/**
 * 사용자 권한 부여에 실패했을 때 발생하는 예외입니다.
 */
open class AuthorizationException(
    message: String = ErrorCode.ACCESS_DENIED.message,
    errorCode: ErrorCode = ErrorCode.ACCESS_DENIED,
    cause: Throwable? = null
) : SimpleChatException(errorCode, message, cause)
