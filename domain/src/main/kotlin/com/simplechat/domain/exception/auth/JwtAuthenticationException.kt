package com.simplechat.domain.exception.auth

import com.simplechat.domain.exception.ErrorCode

/**
 * JWT 인증/유효성 검사에 실패했을 때 발생하는 예외입니다.
 */
class JwtAuthenticationException(
    message: String = ErrorCode.JWT_AUTHENTICATION_FAILED.message,
    errorCode: ErrorCode = ErrorCode.JWT_AUTHENTICATION_FAILED,
    cause: Throwable? = null
) : AuthenticationException(message, errorCode, cause)
