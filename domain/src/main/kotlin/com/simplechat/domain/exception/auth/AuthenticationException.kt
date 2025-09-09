package com.simplechat.domain.exception.auth

import com.simplechat.domain.exception.ErrorCode
import com.simplechat.domain.exception.SimpleChatException

/**
 * 사용자 인증에 실패했을 때 발생하는 예외입니다.
 */
open class AuthenticationException(
    message: String = ErrorCode.AUTHENTICATION_FAILED.message,
    errorCode: ErrorCode = ErrorCode.AUTHENTICATION_FAILED,
    cause: Throwable? = null
) : SimpleChatException(errorCode, message, cause)
