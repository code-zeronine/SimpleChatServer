package com.simplechat.domain.exception

/**
 * 유효성 검사에 실패했을 때 발생하는 예외입니다.
 */
class ValidationException(
    message: String = ErrorCode.VALIDATION_FAILED.message,
    val field: String? = null,
    errorCode: ErrorCode = ErrorCode.VALIDATION_FAILED,
    cause: Throwable? = null
) : SimpleChatException(errorCode, message, cause)
