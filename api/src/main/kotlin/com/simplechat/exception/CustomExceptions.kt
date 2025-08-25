package com.simplechat.exception

/**
 * Base exception class for all custom application exceptions
 */
abstract class SimpleChatException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Exception thrown when a requested resource is not found
 */
open class ResourceNotFoundException(
    message: String,
    cause: Throwable? = null
) : SimpleChatException(message, cause)

/**
 * Exception thrown when user authentication fails
 */
open class AuthenticationException(
    message: String,
    cause: Throwable? = null
) : SimpleChatException(message, cause)

/**
 * Exception thrown when JWT authentication/validation fails
 */
class JwtAuthenticationException(
    message: String,
    cause: Throwable? = null
) : AuthenticationException(message, cause)

/**
 * Exception thrown when user authorization fails
 */
open class AuthorizationException(
    message: String,
    cause: Throwable? = null
) : SimpleChatException(message, cause)

/**
 * Exception thrown when validation fails
 */
class ValidationException(
    message: String,
    val field: String? = null,
    cause: Throwable? = null
) : SimpleChatException(message, cause)

/**
 * Exception thrown when business logic validation fails
 */
open class BusinessLogicException(
    message: String,
    cause: Throwable? = null
) : SimpleChatException(message, cause)

/**
 * Exception thrown when external service call fails
 */
class ExternalServiceException(
    message: String,
    val serviceName: String,
    cause: Throwable? = null
) : SimpleChatException(message, cause)

/**
 * Exception thrown when database operation fails
 */
open class DatabaseException(
    message: String,
    cause: Throwable? = null
) : SimpleChatException(message, cause)

class DatabaseConnectionException(
    message: String = "Failed to connect to database",
    cause: Throwable? = null
) : DatabaseException(message, cause)

class DatabaseOperationException(
    message: String,
    cause: Throwable? = null
) : DatabaseException(message, cause)

/**
 * Exception thrown when user is not found
 */
class UserNotFoundException(message: String) : ResourceNotFoundException(message)

/**
 * Exception thrown when chat room is not found
 */
class ChatRoomNotFoundException(message: String) : ResourceNotFoundException(message)

/**
 * Exception thrown when user-chat room relationship is not found
 */
class UserChatRoomNotFoundException(message: String) : ResourceNotFoundException(message)

/**
 * Exception thrown when user-chat room relationship already exists
 */
class UserChatRoomAlreadyExistsException(message: String) : BusinessLogicException(message)

/**
 * Exception thrown when user has insufficient permissions
 */
class InsufficientPermissionException(message: String) : AuthorizationException(message)