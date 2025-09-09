package com.simplechat.domain.exception.entity

import com.simplechat.domain.exception.ErrorCode
import com.simplechat.domain.exception.SimpleChatException

/**
 * 요청된 리소스를 찾을 수 없을 때 발생하는 예외입니다.
 */
open class ResourceNotFoundException(
    message: String = ErrorCode.RESOURCE_NOT_FOUND.message,
    errorCode: ErrorCode = ErrorCode.RESOURCE_NOT_FOUND,
    cause: Throwable? = null
) : SimpleChatException(errorCode, message, cause)
