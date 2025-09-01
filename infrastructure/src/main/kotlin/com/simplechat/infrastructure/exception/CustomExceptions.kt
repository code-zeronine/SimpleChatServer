package com.simplechat.infrastructure.exception

/**
 * 표준화된 오류 코드를 정의합니다.
 * 각 오류는 고유한 코드와 기본 메시지를 가집니다.
 */
enum class ErrorCode(val code: String, val message: String) {
    // General
    UNKNOWN_ERROR("GEN-001", "알 수 없는 오류가 발생했습니다."),
    INVALID_ARGUMENT("GEN-002", "유효하지 않은 인자입니다."),
    INVALID_STATE("GEN-003", "유효하지 않은 상태입니다."),

    // Authentication & Authorization
    AUTHENTICATION_FAILED("AUTH-001", "인증에 실패했습니다."),
    JWT_AUTHENTICATION_FAILED("AUTH-002", "JWT 인증에 실패했습니다."),
    ACCESS_DENIED("AUTH-003", "접근이 거부되었습니다."),
    INSUFFICIENT_PERMISSION("AUTH-004", "권한이 부족합니다."),

    // Resource
    RESOURCE_NOT_FOUND("RES-001", "리소스를 찾을 수 없습니다."),
    USER_NOT_FOUND("RES-002", "사용자를 찾을 수 없습니다."),
    CHAT_ROOM_NOT_FOUND("RES-003", "채팅방을 찾을 수 없습니다."),
    USER_CHAT_ROOM_NOT_FOUND("RES-004", "사용자-채팅방 관계를 찾을 수 없습니다."),

    // Business Logic
    BUSINESS_LOGIC_ERROR("BIZ-001", "비즈니스 로직 오류가 발생했습니다."),
    VALIDATION_FAILED("BIZ-002", "유효성 검사에 실패했습니다."),
    DUPLICATE_EMAIL("BIZ-003", "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME("BIZ-004", "이미 사용 중인 닉네임입니다."),
    CHAT_ROOM_FULL("BIZ-005", "채팅방이 가득 찼습니다."),
    USER_ALREADY_IN_CHAT_ROOM("BIZ-006", "사용자가 이미 채팅방에 참여하고 있습니다."),
    CANNOT_KICK_SELF("BIZ-007", "자기 자신을 추방할 수 없습니다."),
    CANNOT_CHANGE_OWN_ROLE("BIZ-008", "자신의 역할을 변경할 수 없습니다."),
    PRIVATE_ROOM_INVITE_ONLY("BIZ-009", "비공개 채팅방은 초대를 통해서만 참여할 수 있습니다."),

    // External Service
    EXTERNAL_SERVICE_ERROR("EXT-001", "외부 서비스 오류가 발생했습니다."),

    // Database
    DATABASE_ERROR("DB-001", "데이터베이스 오류가 발생했습니다."),
    DATABASE_CONNECTION_FAILED("DB-002", "데이터베이스 연결에 실패했습니다."),
    DATABASE_OPERATION_FAILED("DB-003", "데이터베이스 작업에 실패했습니다.");
}

/**
 * 모든 사용자 정의 애플리케이션 예외의 기본 클래스입니다.
 * 표준화된 오류 코드와 메시지를 포함합니다.
 */
abstract class SimpleChatException(
    val errorCode: ErrorCode,
    message: String = errorCode.message,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * 요청된 리소스를 찾을 수 없을 때 발생하는 예외입니다.
 */
open class ResourceNotFoundException(
    message: String = ErrorCode.RESOURCE_NOT_FOUND.message,
    errorCode: ErrorCode = ErrorCode.RESOURCE_NOT_FOUND,
    cause: Throwable? = null
) : SimpleChatException(errorCode, message, cause)

/**
 * 사용자 인증에 실패했을 때 발생하는 예외입니다.
 */
open class AuthenticationException(
    message: String = ErrorCode.AUTHENTICATION_FAILED.message,
    errorCode: ErrorCode = ErrorCode.AUTHENTICATION_FAILED,
    cause: Throwable? = null
) : SimpleChatException(errorCode, message, cause)

/**
 * JWT 인증/유효성 검사에 실패했을 때 발생하는 예외입니다.
 */
class JwtAuthenticationException(
    message: String = ErrorCode.JWT_AUTHENTICATION_FAILED.message,
    errorCode: ErrorCode = ErrorCode.JWT_AUTHENTICATION_FAILED,
    cause: Throwable? = null
) : AuthenticationException(message, errorCode, cause)

/**
 * 사용자 권한 부여에 실패했을 때 발생하는 예외입니다.
 */
open class AuthorizationException(
    message: String = ErrorCode.ACCESS_DENIED.message,
    errorCode: ErrorCode = ErrorCode.ACCESS_DENIED,
    cause: Throwable? = null
) : SimpleChatException(errorCode, message, cause)

/**
 * 유효성 검사에 실패했을 때 발생하는 예외입니다.
 */
class ValidationException(
    message: String = ErrorCode.VALIDATION_FAILED.message,
    val field: String? = null,
    errorCode: ErrorCode = ErrorCode.VALIDATION_FAILED,
    cause: Throwable? = null
) : SimpleChatException(errorCode, message, cause)

/**
 * 비즈니스 로직 유효성 검사에 실패했을 때 발생하는 예외입니다.
 */
open class BusinessLogicException(
    message: String = ErrorCode.BUSINESS_LOGIC_ERROR.message,
    errorCode: ErrorCode = ErrorCode.BUSINESS_LOGIC_ERROR,
    cause: Throwable? = null
) : SimpleChatException(errorCode, message, cause)

/**
 * 외부 서비스 호출에 실패했을 때 발생하는 예외입니다.
 */
class ExternalServiceException(
    message: String = ErrorCode.EXTERNAL_SERVICE_ERROR.message,
    val serviceName: String,
    errorCode: ErrorCode = ErrorCode.EXTERNAL_SERVICE_ERROR,
    cause: Throwable? = null
) : SimpleChatException(errorCode, message, cause)

/**
 * 데이터베이스 작업에 실패했을 때 발생하는 예외입니다.
 */
open class DatabaseException(
    message: String = ErrorCode.DATABASE_ERROR.message,
    errorCode: ErrorCode = ErrorCode.DATABASE_ERROR,
    cause: Throwable? = null
) : SimpleChatException(errorCode, message, cause)

class DatabaseConnectionException(
    message: String = ErrorCode.DATABASE_CONNECTION_FAILED.message,
    errorCode: ErrorCode = ErrorCode.DATABASE_CONNECTION_FAILED,
    cause: Throwable? = null
) : DatabaseException(message, errorCode, cause)

class DatabaseOperationException(
    message: String = ErrorCode.DATABASE_OPERATION_FAILED.message,
    errorCode: ErrorCode = ErrorCode.DATABASE_OPERATION_FAILED,
    cause: Throwable? = null
) : DatabaseException(message, errorCode, cause)

/**
 * 사용자를 찾을 수 없을 때 발생하는 예외입니다.
 */
class UserNotFoundException(
    message: String = ErrorCode.USER_NOT_FOUND.message,
    errorCode: ErrorCode = ErrorCode.USER_NOT_FOUND,
    cause: Throwable? = null
) : ResourceNotFoundException(message, errorCode, cause)

/**
 * 채팅방을 찾을 수 없을 때 발생하는 예외입니다.
 */
class ChatRoomNotFoundException(
    message: String = ErrorCode.CHAT_ROOM_NOT_FOUND.message,
    errorCode: ErrorCode = ErrorCode.CHAT_ROOM_NOT_FOUND,
    cause: Throwable? = null
) : ResourceNotFoundException(message, errorCode, cause)

/**
 * 사용자-채팅방 관계를 찾을 수 없을 때 발생하는 예외입니다.
 */
class UserChatRoomNotFoundException(
    message: String = ErrorCode.USER_CHAT_ROOM_NOT_FOUND.message,
    errorCode: ErrorCode = ErrorCode.USER_CHAT_ROOM_NOT_FOUND,
    cause: Throwable? = null
) : ResourceNotFoundException(message, errorCode, cause)

/**
 * 사용자-채팅방 관계가 이미 존재할 때 발생하는 예외입니다.
 */
class UserChatRoomAlreadyExistsException(
    message: String = ErrorCode.USER_ALREADY_IN_CHAT_ROOM.message,
    errorCode: ErrorCode = ErrorCode.USER_ALREADY_IN_CHAT_ROOM,
    cause: Throwable? = null
) : BusinessLogicException(message, errorCode, cause)

/**
 * 사용자에게 권한이 부족할 때 발생하는 예외입니다.
 */
class InsufficientPermissionException(
    message: String = ErrorCode.INSUFFICIENT_PERMISSION.message,
    errorCode: ErrorCode = ErrorCode.INSUFFICIENT_PERMISSION,
    cause: Throwable? = null
) : AuthorizationException(message, errorCode, cause)
