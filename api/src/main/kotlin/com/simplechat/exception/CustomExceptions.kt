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
class ResourceNotFoundException(
    message: String,
    cause: Throwable? = null
) : SimpleChatException(message, cause)

/**
 * Exception thrown when user authentication fails
 */
class AuthenticationException(
    message: String,
    cause: Throwable? = null
) : SimpleChatException(message, cause)

/**
 * Exception thrown when user authorization fails
 */
class AuthorizationException(
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
class BusinessLogicException(
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
class DatabaseException(
    message: String,
    cause: Throwable? = null
) : SimpleChatException(message, cause)