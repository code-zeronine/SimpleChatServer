package com.simplechat.domain.exception

import com.simplechat.domain.exception.auth.AuthenticationException
import com.simplechat.domain.exception.auth.AuthorizationException
import com.simplechat.domain.exception.auth.InsufficientPermissionException
import com.simplechat.domain.exception.auth.JwtAuthenticationException
import com.simplechat.domain.exception.business.BusinessLogicException
import com.simplechat.domain.exception.business.UserChatRoomAlreadyExistsException
import com.simplechat.domain.exception.database.DatabaseConnectionException
import com.simplechat.domain.exception.database.DatabaseException
import com.simplechat.domain.exception.database.DatabaseOperationException
import com.simplechat.domain.exception.entity.ChatRoomNotFoundException
import com.simplechat.domain.exception.entity.ResourceNotFoundException
import com.simplechat.domain.exception.entity.UserChatRoomNotFoundException
import com.simplechat.domain.exception.entity.UserNotFoundException
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Exception 계층구조 테스트
 * 
 * 테스트 대상:
 * - ErrorCode enum 테스트
 * - SimpleChatException 기본 클래스 테스트
 * - 각 Exception 하위 클래스들의 상속 관계 테스트
 * - Exception 생성 및 속성 테스트
 */
class ExceptionHierarchyTest : BehaviorSpec({

    Given("ErrorCode enum 테스트") {
        When("모든 에러 코드를 확인하면") {
            val allErrorCodes = ErrorCode.entries
            
            Then("정의된 에러 코드들이 존재해야 한다") {
                allErrorCodes.size.shouldBeGreaterThan(0)
            }
            
            Then("각 에러 코드가 코드와 메시지를 가져야 한다") {
                allErrorCodes.forEach { errorCode ->
                    errorCode.code.shouldNotBe("")
                    errorCode.message.shouldNotBe("")
                }
            }
        }

        When("에러 코드 카테고리별로 확인하면") {
            Then("일반 에러 코드들이 올바른 형식을 가져야 한다") {
                ErrorCode.UNKNOWN_ERROR.code shouldBe "GEN-001"
                ErrorCode.INVALID_ARGUMENT.code shouldBe "GEN-002"
                ErrorCode.INVALID_STATE.code shouldBe "GEN-003"
            }
            
            Then("인증 에러 코드들이 올바른 형식을 가져야 한다") {
                ErrorCode.AUTHENTICATION_FAILED.code shouldBe "AUTH-001"
                ErrorCode.JWT_AUTHENTICATION_FAILED.code shouldBe "AUTH-002"
                ErrorCode.ACCESS_DENIED.code shouldBe "AUTH-003"
                ErrorCode.INSUFFICIENT_PERMISSION.code shouldBe "AUTH-004"
                ErrorCode.MAX_SESSION_EXCEEDED.code shouldBe "AUTH-005"
                ErrorCode.DUPLICATE_LOGIN_DENIED.code shouldBe "AUTH-006"
            }
            
            Then("리소스 에러 코드들이 올바른 형식을 가져야 한다") {
                ErrorCode.RESOURCE_NOT_FOUND.code shouldBe "RES-001"
                ErrorCode.USER_NOT_FOUND.code shouldBe "RES-002"
                ErrorCode.CHAT_ROOM_NOT_FOUND.code shouldBe "RES-003"
                ErrorCode.USER_CHAT_ROOM_NOT_FOUND.code shouldBe "RES-004"
            }
            
            Then("비즈니스 로직 에러 코드들이 올바른 형식을 가져야 한다") {
                ErrorCode.BUSINESS_LOGIC_ERROR.code shouldBe "BIZ-001"
                ErrorCode.VALIDATION_FAILED.code shouldBe "BIZ-002"
                ErrorCode.DUPLICATE_EMAIL.code shouldBe "BIZ-003"
                ErrorCode.DUPLICATE_NICKNAME.code shouldBe "BIZ-004"
                ErrorCode.CHAT_ROOM_FULL.code shouldBe "BIZ-005"
                ErrorCode.USER_ALREADY_IN_CHAT_ROOM.code shouldBe "BIZ-006"
                ErrorCode.CANNOT_KICK_SELF.code shouldBe "BIZ-007"
                ErrorCode.CANNOT_CHANGE_OWN_ROLE.code shouldBe "BIZ-008"
                ErrorCode.PRIVATE_ROOM_INVITE_ONLY.code shouldBe "BIZ-009"
            }
            
            Then("데이터베이스 에러 코드들이 올바른 형식을 가져야 한다") {
                ErrorCode.DATABASE_ERROR.code shouldBe "DB-001"
                ErrorCode.DATABASE_CONNECTION_FAILED.code shouldBe "DB-002"
                ErrorCode.DATABASE_OPERATION_FAILED.code shouldBe "DB-003"
            }
            
            Then("외부 서비스 에러 코드가 올바른 형식을 가져야 한다") {
                ErrorCode.EXTERNAL_SERVICE_ERROR.code shouldBe "EXT-001"
            }
        }
    }

    Given("ValidationException 테스트") {
        When("기본 생성자로 예외를 생성하면") {
            val exception = ValidationException()

            Then("기본 값들이 올바르게 설정되어야 한다") {
                exception.errorCode shouldBe ErrorCode.VALIDATION_FAILED
                exception.message shouldBe ErrorCode.VALIDATION_FAILED.message
                exception.field shouldBe null
                exception.cause shouldBe null
                exception.shouldBeInstanceOf<SimpleChatException>()
            }
        }

        When("필드 정보와 함께 예외를 생성하면") {
            val customMessage = "사용자명이 유효하지 않습니다"
            val fieldName = "username"
            val exception = ValidationException(
                message = customMessage,
                field = fieldName
            )

            Then("지정한 정보가 올바르게 설정되어야 한다") {
                exception.message shouldBe customMessage
                exception.field shouldBe fieldName
                exception.errorCode shouldBe ErrorCode.VALIDATION_FAILED
            }
        }

        When("모든 파라미터를 지정하여 예외를 생성하면") {
            val customMessage = "커스텀 유효성 검사 오류"
            val fieldName = "email"
            val customErrorCode = ErrorCode.DUPLICATE_EMAIL
            val cause = IllegalArgumentException("원인 예외")
            
            val exception = ValidationException(
                message = customMessage,
                field = fieldName,
                errorCode = customErrorCode,
                cause = cause
            )

            Then("모든 속성이 올바르게 설정되어야 한다") {
                exception.message shouldBe customMessage
                exception.field shouldBe fieldName
                exception.errorCode shouldBe customErrorCode
                exception.cause shouldBe cause
            }
        }
    }

    Given("ExternalServiceException 테스트") {
        When("기본 파라미터로 예외를 생성하면") {
            val serviceName = "Payment Service"
            val exception = ExternalServiceException(serviceName = serviceName)

            Then("기본 값들이 올바르게 설정되어야 한다") {
                exception.errorCode shouldBe ErrorCode.EXTERNAL_SERVICE_ERROR
                exception.message shouldBe ErrorCode.EXTERNAL_SERVICE_ERROR.message
                exception.serviceName shouldBe serviceName
                exception.cause shouldBe null
            }
        }

        When("모든 파라미터를 지정하여 예외를 생성하면") {
            val customMessage = "결제 서비스 연결 실패"
            val serviceName = "Payment Gateway"
            val cause = RuntimeException("Connection timeout")
            
            val exception = ExternalServiceException(
                message = customMessage,
                serviceName = serviceName,
                cause = cause
            )

            Then("지정한 정보가 올바르게 설정되어야 한다") {
                exception.message shouldBe customMessage
                exception.serviceName shouldBe serviceName
                exception.errorCode shouldBe ErrorCode.EXTERNAL_SERVICE_ERROR
                exception.cause shouldBe cause
            }
        }
    }

    Given("AuthenticationException 계층 테스트") {
        When("기본 AuthenticationException을 생성하면") {
            val exception = AuthenticationException()

            Then("올바른 기본값과 상속 관계를 가져야 한다") {
                exception.errorCode shouldBe ErrorCode.AUTHENTICATION_FAILED
                exception.message shouldBe ErrorCode.AUTHENTICATION_FAILED.message
                exception.shouldBeInstanceOf<SimpleChatException>()
                exception.shouldBeInstanceOf<RuntimeException>()
            }
        }

        When("JwtAuthenticationException을 생성하면") {
            val exception = JwtAuthenticationException()

            Then("JWT 관련 에러 코드를 가져야 한다") {
                exception.errorCode shouldBe ErrorCode.JWT_AUTHENTICATION_FAILED
                exception.shouldBeInstanceOf<AuthenticationException>()
                exception.shouldBeInstanceOf<SimpleChatException>()
            }
        }

        When("AuthorizationException을 생성하면") {
            val exception = AuthorizationException()

            Then("인가 관련 에러 코드를 가져야 한다") {
                exception.errorCode shouldBe ErrorCode.ACCESS_DENIED
                exception.shouldBeInstanceOf<SimpleChatException>()
            }
        }

        When("InsufficientPermissionException을 생성하면") {
            val exception = InsufficientPermissionException()

            Then("권한 부족 에러 코드를 가져야 한다") {
                exception.errorCode shouldBe ErrorCode.INSUFFICIENT_PERMISSION
                exception.shouldBeInstanceOf<SimpleChatException>()
            }
        }
    }

    Given("BusinessLogicException 계층 테스트") {
        When("기본 BusinessLogicException을 생성하면") {
            val exception = BusinessLogicException()

            Then("올바른 기본값과 상속 관계를 가져야 한다") {
                exception.errorCode shouldBe ErrorCode.BUSINESS_LOGIC_ERROR
                exception.message shouldBe ErrorCode.BUSINESS_LOGIC_ERROR.message
                exception.shouldBeInstanceOf<SimpleChatException>()
            }
        }

        When("UserChatRoomAlreadyExistsException을 생성하면") {
            val exception = UserChatRoomAlreadyExistsException()

            Then("올바른 에러 코드와 상속 관계를 가져야 한다") {
                exception.errorCode shouldBe ErrorCode.USER_ALREADY_IN_CHAT_ROOM
                exception.shouldBeInstanceOf<BusinessLogicException>()
                exception.shouldBeInstanceOf<SimpleChatException>()
            }
        }
    }

    Given("ResourceNotFoundException 계층 테스트") {
        When("기본 ResourceNotFoundException을 생성하면") {
            val exception = ResourceNotFoundException()

            Then("올바른 기본값과 상속 관계를 가져야 한다") {
                exception.errorCode shouldBe ErrorCode.RESOURCE_NOT_FOUND
                exception.message shouldBe ErrorCode.RESOURCE_NOT_FOUND.message
                exception.shouldBeInstanceOf<SimpleChatException>()
            }
        }

        When("UserNotFoundException을 생성하면") {
            val exception = UserNotFoundException()

            Then("사용자 관련 에러 코드를 가져야 한다") {
                exception.errorCode shouldBe ErrorCode.USER_NOT_FOUND
                exception.shouldBeInstanceOf<ResourceNotFoundException>()
                exception.shouldBeInstanceOf<SimpleChatException>()
            }
        }

        When("ChatRoomNotFoundException을 생성하면") {
            val exception = ChatRoomNotFoundException()

            Then("채팅방 관련 에러 코드를 가져야 한다") {
                exception.errorCode shouldBe ErrorCode.CHAT_ROOM_NOT_FOUND
                exception.shouldBeInstanceOf<ResourceNotFoundException>()
                exception.shouldBeInstanceOf<SimpleChatException>()
            }
        }

        When("UserChatRoomNotFoundException을 생성하면") {
            val exception = UserChatRoomNotFoundException()

            Then("사용자-채팅방 관계 관련 에러 코드를 가져야 한다") {
                exception.errorCode shouldBe ErrorCode.USER_CHAT_ROOM_NOT_FOUND
                exception.shouldBeInstanceOf<ResourceNotFoundException>()
                exception.shouldBeInstanceOf<SimpleChatException>()
            }
        }
    }

    Given("DatabaseException 계층 테스트") {
        When("기본 DatabaseException을 생성하면") {
            val exception = DatabaseException()

            Then("올바른 기본값과 상속 관계를 가져야 한다") {
                exception.errorCode shouldBe ErrorCode.DATABASE_ERROR
                exception.message shouldBe ErrorCode.DATABASE_ERROR.message
                exception.shouldBeInstanceOf<SimpleChatException>()
            }
        }

        When("DatabaseConnectionException을 생성하면") {
            val exception = DatabaseConnectionException()

            Then("데이터베이스 연결 관련 에러 코드를 가져야 한다") {
                exception.errorCode shouldBe ErrorCode.DATABASE_CONNECTION_FAILED
                exception.shouldBeInstanceOf<DatabaseException>()
                exception.shouldBeInstanceOf<SimpleChatException>()
            }
        }

        When("DatabaseOperationException을 생성하면") {
            val exception = DatabaseOperationException()

            Then("데이터베이스 작업 관련 에러 코드를 가져야 한다") {
                exception.errorCode shouldBe ErrorCode.DATABASE_OPERATION_FAILED
                exception.shouldBeInstanceOf<DatabaseException>()
                exception.shouldBeInstanceOf<SimpleChatException>()
            }
        }
    }

    Given("Exception 메시지 및 원인 테스트") {
        When("커스텀 메시지로 예외를 생성하면") {
            val customMessage = "커스텀 오류 메시지"
            val exception = ValidationException(message = customMessage)

            Then("커스텀 메시지가 설정되어야 한다") {
                exception.message shouldBe customMessage
            }
        }

        When("원인 예외와 함께 예외를 생성하면") {
            val cause = IllegalStateException("원인 예외")
            val exception = AuthenticationException(cause = cause)

            Then("원인 예외가 올바르게 설정되어야 한다") {
                exception.cause shouldBe cause
                exception.cause!!.message shouldBe "원인 예외"
            }
        }

        When("예외 체인을 생성하면") {
            val rootCause = IllegalArgumentException("루트 원인")
            val middleCause = ValidationException(message = "중간 예외", cause = rootCause)
            val topException = AuthenticationException(message = "최상위 예외", cause = middleCause)

            Then("예외 체인이 올바르게 연결되어야 한다") {
                topException.message shouldBe "최상위 예외"
                topException.cause shouldBe middleCause
                topException.cause!!.cause shouldBe rootCause
                topException.cause!!.cause!!.message shouldBe "루트 원인"
            }
        }
    }

    Given("Exception 타입별 특성 테스트") {
        When("다양한 에러 코드로 예외들을 생성하면") {
            val exceptions = listOf(
                ValidationException() to ErrorCode.VALIDATION_FAILED,
                AuthenticationException() to ErrorCode.AUTHENTICATION_FAILED,
                JwtAuthenticationException() to ErrorCode.JWT_AUTHENTICATION_FAILED,
                AuthorizationException() to ErrorCode.ACCESS_DENIED,
                InsufficientPermissionException() to ErrorCode.INSUFFICIENT_PERMISSION,
                BusinessLogicException() to ErrorCode.BUSINESS_LOGIC_ERROR,
                UserChatRoomAlreadyExistsException() to ErrorCode.USER_ALREADY_IN_CHAT_ROOM,
                ResourceNotFoundException() to ErrorCode.RESOURCE_NOT_FOUND,
                UserNotFoundException() to ErrorCode.USER_NOT_FOUND,
                ChatRoomNotFoundException() to ErrorCode.CHAT_ROOM_NOT_FOUND,
                UserChatRoomNotFoundException() to ErrorCode.USER_CHAT_ROOM_NOT_FOUND,
                DatabaseException() to ErrorCode.DATABASE_ERROR,
                DatabaseConnectionException() to ErrorCode.DATABASE_CONNECTION_FAILED,
                DatabaseOperationException() to ErrorCode.DATABASE_OPERATION_FAILED,
                ExternalServiceException(serviceName = "Test Service") to ErrorCode.EXTERNAL_SERVICE_ERROR
            )

            Then("각 예외가 올바른 에러 코드를 가져야 한다") {
                exceptions.forEach { (exception, expectedErrorCode) ->
                    exception.errorCode shouldBe expectedErrorCode
                    exception.message shouldBe expectedErrorCode.message
                    exception.shouldBeInstanceOf<SimpleChatException>()
                }
            }
        }

        When("예외 메시지에 에러 코드가 포함되는지 확인하면") {
            val exceptions = mapOf(
                ValidationException() to "BIZ-002",
                AuthenticationException() to "AUTH-001",
                UserNotFoundException() to "RES-002",
                DatabaseException() to "DB-001"
            )

            Then("각 예외의 에러 코드가 올바른 형식이어야 한다") {
                exceptions.forEach { (exception, expectedCode) ->
                    exception.errorCode.code shouldBe expectedCode
                }
            }
        }
    }

    Given("상속 관계 검증 테스트") {
        When("모든 예외 클래스의 상속 관계를 확인하면") {
            val authExceptions = listOf(
                AuthenticationException(),
                JwtAuthenticationException(),
                AuthorizationException(),
                InsufficientPermissionException()
            )

            val businessExceptions = listOf(
                BusinessLogicException(),
                UserChatRoomAlreadyExistsException()
            )

            val entityExceptions = listOf(
                ResourceNotFoundException(),
                UserNotFoundException(),
                ChatRoomNotFoundException(),
                UserChatRoomNotFoundException()
            )

            val databaseExceptions = listOf(
                DatabaseException(),
                DatabaseConnectionException(),
                DatabaseOperationException()
            )

            Then("모든 예외가 SimpleChatException을 상속해야 한다") {
                (authExceptions + businessExceptions + entityExceptions + databaseExceptions).forEach { exception ->
                    exception.shouldBeInstanceOf<SimpleChatException>()
                    exception.shouldBeInstanceOf<RuntimeException>()
                }
            }

            Then("인증 관련 예외들이 올바른 상속 관계를 가져야 한다") {
                JwtAuthenticationException().shouldBeInstanceOf<AuthenticationException>()
                // AuthorizationException과 InsufficientPermissionException은 직접 SimpleChatException을 상속
            }

            Then("비즈니스 로직 예외들이 올바른 상속 관계를 가져야 한다") {
                UserChatRoomAlreadyExistsException().shouldBeInstanceOf<BusinessLogicException>()
            }

            Then("엔티티 관련 예외들이 올바른 상속 관계를 가져야 한다") {
                UserNotFoundException().shouldBeInstanceOf<ResourceNotFoundException>()
                ChatRoomNotFoundException().shouldBeInstanceOf<ResourceNotFoundException>()
                UserChatRoomNotFoundException().shouldBeInstanceOf<ResourceNotFoundException>()
            }

            Then("데이터베이스 예외들이 올바른 상속 관계를 가져야 한다") {
                DatabaseConnectionException().shouldBeInstanceOf<DatabaseException>()
                DatabaseOperationException().shouldBeInstanceOf<DatabaseException>()
            }
        }
    }
})