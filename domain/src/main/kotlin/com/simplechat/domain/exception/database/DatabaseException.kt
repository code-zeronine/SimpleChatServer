package com.simplechat.domain.exception.database

import com.simplechat.domain.exception.ErrorCode
import com.simplechat.domain.exception.SimpleChatException

/**
 * 데이터베이스 작업에 실패했을 때 발생하는 예외입니다.
 */
open class DatabaseException(
    message: String = ErrorCode.DATABASE_ERROR.message,
    errorCode: ErrorCode = ErrorCode.DATABASE_ERROR,
    cause: Throwable? = null
) : SimpleChatException(errorCode, message, cause)
