package com.simplechat.domain.exception.business

import com.simplechat.domain.exception.ErrorCode
import com.simplechat.domain.exception.SimpleChatException

/**
 * 비즈니스 로직 유효성 검사에 실패했을 때 발생하는 예외입니다.
 */
open class BusinessLogicException(
    message: String = ErrorCode.BUSINESS_LOGIC_ERROR.message,
    errorCode: ErrorCode = ErrorCode.BUSINESS_LOGIC_ERROR,
    cause: Throwable? = null
) : SimpleChatException(errorCode, message, cause)
