package com.simplechat.domain.exception

/**
 * 외부 서비스 호출에 실패했을 때 발생하는 예외입니다.
 */
class ExternalServiceException(
    message: String = ErrorCode.EXTERNAL_SERVICE_ERROR.message,
    val serviceName: String,
    errorCode: ErrorCode = ErrorCode.EXTERNAL_SERVICE_ERROR,
    cause: Throwable? = null
) : SimpleChatException(errorCode, message, cause)
