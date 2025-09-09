package com.simplechat.domain.exception.database

import com.simplechat.domain.exception.ErrorCode

class DatabaseOperationException(
    message: String = ErrorCode.DATABASE_OPERATION_FAILED.message,
    errorCode: ErrorCode = ErrorCode.DATABASE_OPERATION_FAILED,
    cause: Throwable? = null
) : DatabaseException(message, errorCode, cause)
