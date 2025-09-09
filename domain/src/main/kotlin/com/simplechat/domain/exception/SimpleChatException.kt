package com.simplechat.domain.exception

/**
 * 모든 사용자 정의 애플리케이션 예외의 기본 클래스입니다.
 * 표준화된 오류 코드와 메시지를 포함합니다.
 */
abstract class SimpleChatException(
    val errorCode: ErrorCode,
    message: String = errorCode.message,
    cause: Throwable? = null
) : RuntimeException(message, cause)
