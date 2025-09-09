package com.simplechat.domain.exception.database

import com.simplechat.domain.exception.ErrorCode

class DatabaseConnectionException(
    message: String = ErrorCode.DATABASE_CONNECTION_FAILED.message,
    errorCode: ErrorCode = ErrorCode.DATABASE_CONNECTION_FAILED,
    cause: Throwable? = null
) : DatabaseException(message, errorCode, cause)
