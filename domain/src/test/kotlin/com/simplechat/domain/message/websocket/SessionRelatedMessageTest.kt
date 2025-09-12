package com.simplechat.domain.message.websocket

import com.simplechat.domain.exception.websocket.WebSocketSessionException
import com.simplechat.domain.exception.websocket.WebSocketErrorCode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.maps.shouldContainKeys
import java.time.Instant

/**
 * 세션 관련 메시지 도메인 클래스들 테스트
 * - SessionTerminationReason enum 테스트
 * - SessionTerminatedWebSocketMessage data class 테스트
 * - WebSocketSessionException 클래스 테스트
 * - WebSocketErrorCode enum 테스트
 */
class SessionRelatedMessageTest : BehaviorSpec({

    Given("SessionTerminationReason enum 테스트") {
        When("모든 종료 이유 상수를 확인하면") {
            val allReasons = SessionTerminationReason.entries
            
            Then("6개의 종료 이유가 정의되어야 한다") {
                allReasons shouldHaveSize 6
            }
            
            Then("각 종료 이유가 올바른 이름을 가져야 한다") {
                allReasons.shouldContain(SessionTerminationReason.DUPLICATE_LOGIN)
                allReasons.shouldContain(SessionTerminationReason.FORCED_LOGOUT)
                allReasons.shouldContain(SessionTerminationReason.SECURITY_VIOLATION)
                allReasons.shouldContain(SessionTerminationReason.INACTIVITY_TIMEOUT)
                allReasons.shouldContain(SessionTerminationReason.USER_REQUEST)
                allReasons.shouldContain(SessionTerminationReason.SYSTEM_MAINTENANCE)
            }
        }
        
        When("toString()을 호출하면") {
            Then("enum 이름이 반환되어야 한다") {
                SessionTerminationReason.DUPLICATE_LOGIN.toString() shouldBe "DUPLICATE_LOGIN"
                SessionTerminationReason.FORCED_LOGOUT.toString() shouldBe "FORCED_LOGOUT"
                SessionTerminationReason.SECURITY_VIOLATION.toString() shouldBe "SECURITY_VIOLATION"
                SessionTerminationReason.INACTIVITY_TIMEOUT.toString() shouldBe "INACTIVITY_TIMEOUT"
                SessionTerminationReason.USER_REQUEST.toString() shouldBe "USER_REQUEST"
                SessionTerminationReason.SYSTEM_MAINTENANCE.toString() shouldBe "SYSTEM_MAINTENANCE"
            }
        }
    }

    Given("SessionTerminatedWebSocketMessage 테스트") {
        val testReason = SessionTerminationReason.DUPLICATE_LOGIN
        val testMessage = "중복 로그인으로 인해 세션이 종료됩니다."
        val testGracePeriod = 10
        val testSessionId = "test-session-123"

        When("기본 값으로 메시지를 생성하면") {
            val message = SessionTerminatedWebSocketMessage(
                reason = testReason,
                message = testMessage
            )

            Then("모든 속성이 올바르게 설정되어야 한다") {
                message.type shouldBe WebSocketMessageType.SESSION_TERMINATED
                message.reason shouldBe testReason
                message.message shouldBe testMessage
                message.gracePeriodSeconds shouldBe 5 // 기본값
                message.messageId shouldContain "session-terminated-"
                message.timestamp shouldBeGreaterThan 0L
                message.sessionId shouldBe null // 기본값
            }
        }

        When("모든 값을 명시적으로 설정하여 메시지를 생성하면") {
            val customTimestamp = Instant.now().toEpochMilli()
            val customMessageId = "custom-session-terminated-001"
            
            val message = SessionTerminatedWebSocketMessage(
                messageId = customMessageId,
                timestamp = customTimestamp,
                sessionId = testSessionId,
                reason = testReason,
                message = testMessage,
                gracePeriodSeconds = testGracePeriod
            )

            Then("설정한 값들이 정확히 반영되어야 한다") {
                message.messageId shouldBe customMessageId
                message.timestamp shouldBe customTimestamp
                message.sessionId shouldBe testSessionId
                message.reason shouldBe testReason
                message.message shouldBe testMessage
                message.gracePeriodSeconds shouldBe testGracePeriod
                message.type shouldBe WebSocketMessageType.SESSION_TERMINATED
            }
        }

        When("다른 종료 이유로 메시지를 생성하면") {
            val reasons = listOf(
                SessionTerminationReason.FORCED_LOGOUT to "관리자에 의해 강제 로그아웃됩니다.",
                SessionTerminationReason.SECURITY_VIOLATION to "보안 위반이 감지되어 세션을 종료합니다.",
                SessionTerminationReason.INACTIVITY_TIMEOUT to "비활성 상태로 인해 세션이 만료됩니다.",
                SessionTerminationReason.USER_REQUEST to "사용자 요청에 의해 세션을 종료합니다.",
                SessionTerminationReason.SYSTEM_MAINTENANCE to "시스템 점검을 위해 세션을 종료합니다."
            )

            Then("각 종료 이유에 대해 올바른 메시지가 생성되어야 한다") {
                reasons.forEach { (reason, expectedMessage) ->
                    val message = SessionTerminatedWebSocketMessage(
                        reason = reason,
                        message = expectedMessage
                    )
                    
                    message.reason shouldBe reason
                    message.message shouldBe expectedMessage
                    message.type shouldBe WebSocketMessageType.SESSION_TERMINATED
                }
            }
        }

        When("유예 시간을 다양하게 설정하면") {
            val gracePeriods = listOf(0, 1, 5, 10, 30, 60)

            Then("설정한 유예 시간이 정확히 반영되어야 한다") {
                gracePeriods.forEach { gracePeriod ->
                    val message = SessionTerminatedWebSocketMessage(
                        reason = testReason,
                        message = testMessage,
                        gracePeriodSeconds = gracePeriod
                    )
                    
                    message.gracePeriodSeconds shouldBe gracePeriod
                }
            }
        }
    }

    Given("SessionTerminatedWebSocketMessage 데이터 클래스 동작 테스트") {
        val message1 = SessionTerminatedWebSocketMessage(
            messageId = "test-001",
            timestamp = 1234567890L,
            sessionId = "session-123",
            reason = SessionTerminationReason.DUPLICATE_LOGIN,
            message = "Test message",
            gracePeriodSeconds = 5
        )
        
        val message2 = SessionTerminatedWebSocketMessage(
            messageId = "test-001",
            timestamp = 1234567890L,
            sessionId = "session-123",
            reason = SessionTerminationReason.DUPLICATE_LOGIN,
            message = "Test message",
            gracePeriodSeconds = 5
        )
        
        val message3 = SessionTerminatedWebSocketMessage(
            messageId = "test-002",
            timestamp = 1234567890L,
            sessionId = "session-123",
            reason = SessionTerminationReason.FORCED_LOGOUT,
            message = "Different message",
            gracePeriodSeconds = 10
        )

        When("동일한 값을 가진 두 메시지를 비교하면") {
            Then("equals()가 true를 반환해야 한다") {
                message1 shouldBe message2
            }
            
            Then("hashCode()가 같아야 한다") {
                message1.hashCode() shouldBe message2.hashCode()
            }
        }

        When("다른 값을 가진 메시지와 비교하면") {
            Then("equals()가 false를 반환해야 한다") {
                message1 shouldNotBe message3
            }
        }

        When("toString()을 호출하면") {
            Then("모든 속성이 포함된 문자열이 반환되어야 한다") {
                val result = message1.toString()
                result shouldContain "SessionTerminatedWebSocketMessage"
                result shouldContain "test-001"
                result shouldContain "DUPLICATE_LOGIN"
                result shouldContain "Test message"
                result shouldContain "5"
            }
        }

        When("copy()로 일부 속성을 변경하면") {
            val copiedMessage = message1.copy(
                reason = SessionTerminationReason.USER_REQUEST,
                gracePeriodSeconds = 15
            )

            Then("변경된 속성만 다르고 나머지는 같아야 한다") {
                copiedMessage.messageId shouldBe message1.messageId
                copiedMessage.timestamp shouldBe message1.timestamp
                copiedMessage.sessionId shouldBe message1.sessionId
                copiedMessage.message shouldBe message1.message
                copiedMessage.reason shouldBe SessionTerminationReason.USER_REQUEST
                copiedMessage.gracePeriodSeconds shouldBe 15
                copiedMessage.type shouldBe WebSocketMessageType.SESSION_TERMINATED
            }
        }
    }

    Given("WebSocketErrorCode enum 테스트") {
        When("모든 에러 코드를 확인하면") {
            val allErrorCodes = WebSocketErrorCode.entries
            
            Then("정의된 에러 코드가 존재해야 한다") {
                allErrorCodes.size.shouldBeGreaterThan(0)
            }
            
            Then("각 에러 코드가 코드와 메시지를 가져야 한다") {
                allErrorCodes.forEach { errorCode ->
                    errorCode.code.shouldNotBe("")
                    errorCode.message.shouldNotBe("")
                }
            }
        }

        When("세션 관련 에러 코드들을 확인하면") {
            Then("세션 관련 에러 코드들이 존재해야 한다") {
                WebSocketErrorCode.WS_SESSION_NOT_FOUND.code shouldBe "WS-500"
                WebSocketErrorCode.WS_SESSION_NOT_FOUND.message shouldBe "WebSocket 세션을 찾을 수 없습니다."
                
                WebSocketErrorCode.WS_SESSION_EXPIRED.code shouldBe "WS-501"
                WebSocketErrorCode.WS_SESSION_EXPIRED.message shouldBe "WebSocket 세션이 만료되었습니다."
                
                WebSocketErrorCode.WS_SESSION_INVALID.code shouldBe "WS-502"
                WebSocketErrorCode.WS_SESSION_INVALID.message shouldBe "WebSocket 세션이 유효하지 않습니다."
                
                WebSocketErrorCode.WS_SESSION_CONFLICT.code shouldBe "WS-503"
                WebSocketErrorCode.WS_SESSION_CONFLICT.message shouldBe "WebSocket 세션 충돌이 발생했습니다."
            }
        }

        When("연결 관련 에러 코드들을 확인하면") {
            Then("연결 관련 에러 코드들이 올바른 형식을 가져야 한다") {
                WebSocketErrorCode.WS_CONNECTION_FAILED.code shouldBe "WS-001"
                WebSocketErrorCode.WS_CONNECTION_LIMIT_EXCEEDED.code shouldBe "WS-002"
                WebSocketErrorCode.WS_CONNECTION_TIMEOUT.code shouldBe "WS-003"
                WebSocketErrorCode.WS_CONNECTION_CLOSED.code shouldBe "WS-004"
                WebSocketErrorCode.WS_CONNECTION_INTERRUPTED.code shouldBe "WS-005"
            }
        }

        When("인증 관련 에러 코드들을 확인하면") {
            Then("인증 관련 에러 코드들이 올바른 범위에 있어야 한다") {
                WebSocketErrorCode.WS_AUTHENTICATION_FAILED.code shouldBe "WS-100"
                WebSocketErrorCode.WS_AUTHORIZATION_FAILED.code shouldBe "WS-101"
                WebSocketErrorCode.WS_USER_ID_EXTRACTION_FAILED.code shouldBe "WS-102"
                WebSocketErrorCode.WS_TOKEN_VALIDATION_FAILED.code shouldBe "WS-103"
                WebSocketErrorCode.WS_INVALID_CREDENTIALS.code shouldBe "WS-104"
            }
        }
    }

    Given("WebSocketSessionException 테스트") {
        When("기본 생성자로 예외를 생성하면") {
            val exception = WebSocketSessionException()

            Then("기본 에러 코드와 메시지가 설정되어야 한다") {
                exception.errorCode shouldBe WebSocketErrorCode.WS_SESSION_NOT_FOUND
                exception.message shouldBe WebSocketErrorCode.WS_SESSION_NOT_FOUND.message
                exception.cause shouldBe null
            }
        }

        When("커스텀 메시지로 예외를 생성하면") {
            val customMessage = "커스텀 세션 오류 메시지"
            val exception = WebSocketSessionException(customMessage)

            Then("커스텀 메시지가 설정되어야 한다") {
                exception.errorCode shouldBe WebSocketErrorCode.WS_SESSION_NOT_FOUND
                exception.message shouldBe customMessage
                exception.cause shouldBe null
            }
        }

        When("특정 에러 코드로 예외를 생성하면") {
            val errorCode = WebSocketErrorCode.WS_SESSION_EXPIRED
            val exception = WebSocketSessionException(
                message = errorCode.message,
                errorCode = errorCode
            )

            Then("지정한 에러 코드가 설정되어야 한다") {
                exception.errorCode shouldBe errorCode
                exception.message shouldBe errorCode.message
            }
        }

        When("모든 파라미터를 지정하여 예외를 생성하면") {
            val customMessage = "세션이 만료되었습니다"
            val errorCode = WebSocketErrorCode.WS_SESSION_EXPIRED
            val cause = RuntimeException("원인 예외")
            val exception = WebSocketSessionException(customMessage, errorCode, cause)

            Then("모든 속성이 올바르게 설정되어야 한다") {
                exception.message shouldBe customMessage
                exception.errorCode shouldBe errorCode
                exception.cause shouldBe cause
            }
        }

        When("예외를 에러 맵으로 변환하면") {
            val exception = WebSocketSessionException(
                "세션 에러 테스트",
                WebSocketErrorCode.WS_SESSION_CONFLICT
            )
            val errorMap = exception.toErrorMap()

            Then("에러 맵이 올바른 구조를 가져야 한다") {
                errorMap.shouldContainKeys("type", "errorCode", "message", "timestamp")
                errorMap["type"] shouldBe "ERROR"
                errorMap["errorCode"] shouldBe "WS-503"
                errorMap["message"] shouldBe "세션 에러 테스트"
                errorMap["timestamp"].shouldBeInstanceOf<Long>()
            }
        }
    }

    Given("WebSocketException 서브클래스 테스트") {
        When("WebSocketSessionException이 WebSocketException을 상속하는지 확인하면") {
            val exception = WebSocketSessionException()

            Then("올바른 상속 관계를 가져야 한다") {
                exception.shouldBeInstanceOf<RuntimeException>()
            }
        }

        When("다양한 세션 에러 코드로 예외를 생성하면") {
            val sessionErrorCodes = listOf(
                WebSocketErrorCode.WS_SESSION_NOT_FOUND,
                WebSocketErrorCode.WS_SESSION_EXPIRED,
                WebSocketErrorCode.WS_SESSION_INVALID,
                WebSocketErrorCode.WS_SESSION_CONFLICT
            )

            Then("각 에러 코드에 대해 예외가 올바르게 생성되어야 한다") {
                sessionErrorCodes.forEach { errorCode ->
                    val exception = WebSocketSessionException(
                        message = errorCode.message,
                        errorCode = errorCode
                    )
                    exception.errorCode shouldBe errorCode
                    exception.message shouldBe errorCode.message
                }
            }
        }
    }

    Given("에러 맵 변환 테스트") {
        When("정상적인 예외를 에러 맵으로 변환하면") {
            val exception = WebSocketSessionException(
                message = "테스트 세션 에러",
                errorCode = WebSocketErrorCode.WS_SESSION_NOT_FOUND
            )
            val errorMap = exception.toErrorMap()

            Then("에러 맵이 올바른 구조를 가져야 한다") {
                errorMap.shouldContainKeys("type", "errorCode", "message", "timestamp")
                errorMap["type"] shouldBe "ERROR"
                errorMap["errorCode"] shouldBe "WS-500"
                errorMap["message"] shouldBe "테스트 세션 에러"
                errorMap["timestamp"].shouldBeInstanceOf<Long>()
            }
        }

        When("시간을 두고 에러 맵을 여러 번 생성하면") {
            val exception = WebSocketSessionException()
            val errorMap1 = exception.toErrorMap()
            Thread.sleep(1) // 시간차 생성
            val errorMap2 = exception.toErrorMap()

            Then("각각 다른 타임스탬프를 가져야 한다") {
                val timestamp1 = errorMap1["timestamp"] as Long
                val timestamp2 = errorMap2["timestamp"] as Long
                timestamp2.shouldBeGreaterThan(timestamp1)
            }
        }
    }
})