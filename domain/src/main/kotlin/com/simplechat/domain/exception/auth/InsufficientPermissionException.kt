package com.simplechat.domain.exception.auth

import com.simplechat.domain.exception.ErrorCode

/**
 * 사용자에게 권한이 부족할 때 발생하는 예외입니다.
 */
class InsufficientPermissionException(
    message: String = ErrorCode.INSUFFICIENT_PERMISSION.message,
    errorCode: ErrorCode = ErrorCode.INSUFFICIENT_PERMISSION,
    cause: Throwable? = null
) : AuthorizationException(message, errorCode, cause)
