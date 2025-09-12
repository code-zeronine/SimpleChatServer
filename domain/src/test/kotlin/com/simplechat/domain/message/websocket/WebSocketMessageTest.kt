package com.simplechat.domain.message.websocket

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant

/**
 * WebSocket 메시지 도메인 클래스들 테스트
 *
 * 모든 WebSocket 메시지 타입의 생성, 속성, 불변성을 검증합니다.
 */
class WebSocketMessageTest : BehaviorSpec({

    val testTimestamp = System.currentTimeMillis()
    val testMessageId = "test-msg-123"
    val testSessionId = "session-456"

    Given("ChatWebSocketMessage 테스트 시") {
        When("유효한 데이터로 ChatWebSocketMessage를 생성하면") {
            val chatMessage = ChatWebSocketMessage(
                messageId = testMessageId,
                timestamp = testTimestamp,
                sessionId = testSessionId,
                content = "Hello World!",
                userId = 100L,
                roomId = 1L,
                userNickname = "TestUser"
            )

            Then("모든 속성이 올바르게 설정되어야 한다") {
                chatMessage.type shouldBe WebSocketMessageType.CHAT
                chatMessage.messageId shouldBe testMessageId
                chatMessage.timestamp shouldBe testTimestamp
                chatMessage.sessionId shouldBe testSessionId
                chatMessage.content shouldBe "Hello World!"
                chatMessage.userId shouldBe 100L
                chatMessage.roomId shouldBe 1L
                chatMessage.userNickname shouldBe "TestUser"
            }

            And("WebSocketMessage 타입이어야 한다") {
                chatMessage.shouldBeInstanceOf<WebSocketMessage>()
            }
        }

        When("빈 내용으로 ChatWebSocketMessage를 생성하면") {
            val chatMessage = ChatWebSocketMessage(
                messageId = testMessageId,
                timestamp = testTimestamp,
                sessionId = testSessionId,
                content = "",
                userId = 200L,
                roomId = 2L,
                userNickname = "EmptyUser"
            )

            Then("빈 내용도 허용되어야 한다") {
                chatMessage.content shouldBe ""
                chatMessage.type shouldBe WebSocketMessageType.CHAT
            }
        }

        When("긴 내용으로 ChatWebSocketMessage를 생성하면") {
            val longContent = "a".repeat(1000)
            val chatMessage = ChatWebSocketMessage(
                messageId = testMessageId,
                timestamp = testTimestamp,
                sessionId = testSessionId,
                content = longContent,
                userId = 300L,
                roomId = 3L,
                userNickname = "LongContentUser"
            )

            Then("긴 내용도 허용되어야 한다") {
                chatMessage.content shouldBe longContent
                chatMessage.content.length shouldBe 1000
            }
        }
    }

    Given("JoinWebSocketMessage 테스트 시") {
        When("유효한 데이터로 JoinWebSocketMessage를 생성하면") {
            val joinMessage = JoinWebSocketMessage(
                messageId = testMessageId,
                timestamp = testTimestamp,
                sessionId = testSessionId,
                userId = 100L,
                roomId = 1L,
                userNickname = "NewUser"
            )

            Then("모든 속성이 올바르게 설정되어야 한다") {
                joinMessage.type shouldBe WebSocketMessageType.JOIN
                joinMessage.messageId shouldBe testMessageId
                joinMessage.timestamp shouldBe testTimestamp
                joinMessage.sessionId shouldBe testSessionId
                joinMessage.userId shouldBe 100L
                joinMessage.roomId shouldBe 1L
                joinMessage.userNickname shouldBe "NewUser"
            }
        }

        When("특수 문자가 포함된 닉네임으로 생성하면") {
            val joinMessage = JoinWebSocketMessage(
                messageId = testMessageId,
                timestamp = testTimestamp,
                sessionId = testSessionId,
                userId = 100L,
                roomId = 1L,
                userNickname = "User_123!@#한글"
            )

            Then("특수 문자 닉네임도 허용되어야 한다") {
                joinMessage.userNickname shouldBe "User_123!@#한글"
            }
        }
    }

    Given("LeaveWebSocketMessage 테스트 시") {
        When("유효한 데이터로 LeaveWebSocketMessage를 생성하면") {
            val leaveMessage = LeaveWebSocketMessage(
                messageId = testMessageId,
                timestamp = testTimestamp,
                sessionId = testSessionId,
                userId = 100L,
                roomId = 1L,
                userNickname = "LeavingUser"
            )

            Then("모든 속성이 올바르게 설정되어야 한다") {
                leaveMessage.type shouldBe WebSocketMessageType.LEAVE
                leaveMessage.messageId shouldBe testMessageId
                leaveMessage.userId shouldBe 100L
                leaveMessage.roomId shouldBe 1L
                leaveMessage.userNickname shouldBe "LeavingUser"
            }
        }
    }

    Given("TypingWebSocketMessage 테스트 시") {
        When("타이핑 시작 메시지를 생성하면") {
            val typingMessage = TypingWebSocketMessage(
                messageId = testMessageId,
                timestamp = testTimestamp,
                sessionId = testSessionId,
                userId = 100L,
                roomId = 1L,
                userNickname = "TypingUser",
                isTyping = true
            )

            Then("타이핑 상태가 true여야 한다") {
                typingMessage.type shouldBe WebSocketMessageType.TYPING
                typingMessage.isTyping shouldBe true
                typingMessage.userId shouldBe 100L
                typingMessage.userNickname shouldBe "TypingUser"
            }
        }

        When("타이핑 중지 메시지를 생성하면") {
            val typingMessage = TypingWebSocketMessage(
                messageId = testMessageId,
                timestamp = testTimestamp,
                sessionId = testSessionId,
                userId = 100L,
                roomId = 1L,
                userNickname = "TypingUser",
                isTyping = false
            )

            Then("타이핑 상태가 false여야 한다") {
                typingMessage.isTyping shouldBe false
            }
        }
    }

    Given("SystemWebSocketMessage 테스트 시") {
        When("기본 시스템 메시지를 생성하면") {
            val systemMessage = SystemWebSocketMessage(
                messageId = testMessageId,
                timestamp = testTimestamp,
                sessionId = testSessionId,
                content = "Server maintenance scheduled"
            )

            Then("기본값이 올바르게 설정되어야 한다") {
                systemMessage.type shouldBe WebSocketMessageType.SYSTEM
                systemMessage.content shouldBe "Server maintenance scheduled"
                systemMessage.level shouldBe "INFO"
                systemMessage.userId shouldBe null
                systemMessage.userNickname shouldBe null
            }
        }

        When("사용자 정보가 포함된 시스템 메시지를 생성하면") {
            val systemMessage = SystemWebSocketMessage(
                messageId = testMessageId,
                timestamp = testTimestamp,
                sessionId = testSessionId,
                content = "User joined the room",
                level = "EVENT",
                userId = 200L,
                userNickname = "EventUser"
            )

            Then("모든 속성이 올바르게 설정되어야 한다") {
                systemMessage.content shouldBe "User joined the room"
                systemMessage.level shouldBe "EVENT"
                systemMessage.userId shouldBe 200L
                systemMessage.userNickname shouldBe "EventUser"
            }
        }

        When("다양한 레벨의 시스템 메시지를 생성하면") {
            val levels = listOf("INFO", "WARNING", "ERROR", "DEBUG", "EVENT")
            
            levels.forEach { level ->
                val systemMessage = SystemWebSocketMessage(
                    messageId = testMessageId,
                    timestamp = testTimestamp,
                    sessionId = testSessionId,
                    content = "Test $level message",
                    level = level
                )

                Then("$level 레벨이 올바르게 설정되어야 한다") {
                    systemMessage.level shouldBe level
                }
            }
        }
    }

    Given("ErrorWebSocketMessage 테스트 시") {
        When("기본 에러 메시지를 생성하면") {
            val errorMessage = ErrorWebSocketMessage(
                messageId = testMessageId,
                timestamp = testTimestamp,
                sessionId = testSessionId,
                errorCode = "VALIDATION_ERROR",
                errorMessage = "Invalid input provided"
            )

            Then("모든 속성이 올바르게 설정되어야 한다") {
                errorMessage.type shouldBe WebSocketMessageType.ERROR
                errorMessage.errorCode shouldBe "VALIDATION_ERROR"
                errorMessage.errorMessage shouldBe "Invalid input provided"
                errorMessage.originalMessage shouldBe null
                errorMessage.details shouldBe null
            }
        }

        When("상세 정보가 포함된 에러 메시지를 생성하면") {
            val details = mapOf(
                "field" to "username",
                "reason" to "too_short",
                "minLength" to 3
            )

            val errorMessage = ErrorWebSocketMessage(
                messageId = testMessageId,
                timestamp = testTimestamp,
                sessionId = testSessionId,
                errorCode = "FIELD_VALIDATION_ERROR",
                errorMessage = "Username is too short",
                originalMessage = "original-msg-789",
                details = details
            )

            Then("상세 정보가 올바르게 설정되어야 한다") {
                errorMessage.errorCode shouldBe "FIELD_VALIDATION_ERROR"
                errorMessage.errorMessage shouldBe "Username is too short"
                errorMessage.originalMessage shouldBe "original-msg-789"
                errorMessage.details shouldBe details
                errorMessage.details?.get("field") shouldBe "username"
                errorMessage.details?.get("minLength") shouldBe 3
            }
        }
    }

    Given("HeartbeatWebSocketMessage 테스트 시") {
        When("하트비트 메시지를 생성하면") {
            val heartbeatMessage = HeartbeatWebSocketMessage(
                messageId = testMessageId,
                timestamp = testTimestamp,
                sessionId = testSessionId
            )

            Then("기본 속성이 올바르게 설정되어야 한다") {
                heartbeatMessage.type shouldBe WebSocketMessageType.HEARTBEAT
                heartbeatMessage.messageId shouldBe testMessageId
                heartbeatMessage.timestamp shouldBe testTimestamp
                heartbeatMessage.sessionId shouldBe testSessionId
            }
        }

        When("null 세션 ID로 하트비트 메시지를 생성하면") {
            val heartbeatMessage = HeartbeatWebSocketMessage(
                messageId = testMessageId,
                timestamp = testTimestamp,
                sessionId = null
            )

            Then("null 세션 ID도 허용되어야 한다") {
                heartbeatMessage.sessionId shouldBe null
                heartbeatMessage.type shouldBe WebSocketMessageType.HEARTBEAT
            }
        }
    }

    Given("AckWebSocketMessage 테스트 시") {
        When("기본 확인 메시지를 생성하면") {
            val ackMessage = AckWebSocketMessage(
                messageId = testMessageId,
                timestamp = testTimestamp,
                sessionId = testSessionId,
                originalMessageId = "original-123"
            )

            Then("기본값이 올바르게 설정되어야 한다") {
                ackMessage.type shouldBe WebSocketMessageType.ACK
                ackMessage.originalMessageId shouldBe "original-123"
                ackMessage.status shouldBe "OK"
            }
        }

        When("커스텀 상태로 확인 메시지를 생성하면") {
            val ackMessage = AckWebSocketMessage(
                messageId = testMessageId,
                timestamp = testTimestamp,
                sessionId = testSessionId,
                originalMessageId = "original-456",
                status = "PROCESSED"
            )

            Then("커스텀 상태가 설정되어야 한다") {
                ackMessage.status shouldBe "PROCESSED"
                ackMessage.originalMessageId shouldBe "original-456"
            }
        }

        When("다양한 상태로 확인 메시지를 생성하면") {
            val statuses = listOf("OK", "ERROR", "PROCESSED", "PENDING", "FAILED")
            
            statuses.forEach { status ->
                val ackMessage = AckWebSocketMessage(
                    messageId = testMessageId,
                    timestamp = testTimestamp,
                    sessionId = testSessionId,
                    originalMessageId = "original-$status",
                    status = status
                )

                Then("$status 상태가 올바르게 설정되어야 한다") {
                    ackMessage.status shouldBe status
                }
            }
        }
    }

    Given("WebSocketMessageType enum 테스트 시") {
        When("모든 메시지 타입을 확인하면") {
            val expectedTypes = setOf(
                WebSocketMessageType.CHAT,
                WebSocketMessageType.JOIN,
                WebSocketMessageType.LEAVE,
                WebSocketMessageType.TYPING,
                WebSocketMessageType.HEARTBEAT,
                WebSocketMessageType.SYSTEM,
                WebSocketMessageType.ERROR,
                WebSocketMessageType.ACK,
                WebSocketMessageType.DUPLICATE_LOGIN_DETECTED,
                WebSocketMessageType.SESSION_TERMINATED
            )

            Then("모든 타입이 존재해야 한다") {
                val allTypes = WebSocketMessageType.values().toSet()
                allTypes shouldBe expectedTypes
                allTypes.size shouldBe 10
            }
        }

        When("각 메시지 타입의 이름을 확인하면") {
            Then("올바른 이름을 가져야 한다") {
                WebSocketMessageType.CHAT.name shouldBe "CHAT"
                WebSocketMessageType.JOIN.name shouldBe "JOIN"
                WebSocketMessageType.LEAVE.name shouldBe "LEAVE"
                WebSocketMessageType.TYPING.name shouldBe "TYPING"
                WebSocketMessageType.HEARTBEAT.name shouldBe "HEARTBEAT"
                WebSocketMessageType.SYSTEM.name shouldBe "SYSTEM"
                WebSocketMessageType.ERROR.name shouldBe "ERROR"
                WebSocketMessageType.ACK.name shouldBe "ACK"
                WebSocketMessageType.DUPLICATE_LOGIN_DETECTED.name shouldBe "DUPLICATE_LOGIN_DETECTED"
                WebSocketMessageType.SESSION_TERMINATED.name shouldBe "SESSION_TERMINATED"
            }
        }
    }

    Given("메시지 불변성 테스트 시") {
        When("data class의 불변성을 테스트하면") {
            val originalMessage = ChatWebSocketMessage(
                messageId = testMessageId,
                timestamp = testTimestamp,
                sessionId = testSessionId,
                content = "Original content",
                userId = 100L,
                roomId = 1L,
                userNickname = "OriginalUser"
            )

            val copiedMessage = originalMessage.copy(content = "Modified content")

            Then("원본은 변경되지 않아야 한다") {
                originalMessage.content shouldBe "Original content"
                copiedMessage.content shouldBe "Modified content"
                originalMessage shouldNotBe copiedMessage
            }

            And("공통 속성은 유지되어야 한다") {
                originalMessage.messageId shouldBe copiedMessage.messageId
                originalMessage.timestamp shouldBe copiedMessage.timestamp
                originalMessage.sessionId shouldBe copiedMessage.sessionId
                originalMessage.userId shouldBe copiedMessage.userId
                originalMessage.roomId shouldBe copiedMessage.roomId
                originalMessage.userNickname shouldBe copiedMessage.userNickname
            }
        }

        When("equals와 hashCode를 테스트하면") {
            val message1 = ChatWebSocketMessage(
                messageId = testMessageId,
                timestamp = testTimestamp,
                sessionId = testSessionId,
                content = "Same content",
                userId = 100L,
                roomId = 1L,
                userNickname = "SameUser"
            )

            val message2 = ChatWebSocketMessage(
                messageId = testMessageId,
                timestamp = testTimestamp,
                sessionId = testSessionId,
                content = "Same content",
                userId = 100L,
                roomId = 1L,
                userNickname = "SameUser"
            )

            val message3 = ChatWebSocketMessage(
                messageId = testMessageId,
                timestamp = testTimestamp,
                sessionId = testSessionId,
                content = "Different content",
                userId = 100L,
                roomId = 1L,
                userNickname = "SameUser"
            )

            Then("동일한 내용의 메시지는 equal해야 한다") {
                message1 shouldBe message2
                message1.hashCode() shouldBe message2.hashCode()
            }

            And("다른 내용의 메시지는 equal하지 않아야 한다") {
                message1 shouldNotBe message3
            }
        }
    }

    Given("타임스탬프 테스트 시") {
        When("현재 시간으로 메시지를 생성하면") {
            val currentTime = System.currentTimeMillis()
            val message = ChatWebSocketMessage(
                messageId = testMessageId,
                timestamp = currentTime,
                sessionId = testSessionId,
                content = "Current time message",
                userId = 100L,
                roomId = 1L,
                userNickname = "TimeUser"
            )

            Then("타임스탬프가 현재 시간과 비슷해야 한다") {
                val timeDiff = Math.abs(message.timestamp - currentTime)
                timeDiff shouldBe 0L // 동일한 시간을 사용했으므로 차이가 없어야 함
            }
        }

        When("과거 시간으로 메시지를 생성하면") {
            val pastTime = Instant.now().minusSeconds(3600).toEpochMilli() // 1시간 전
            val message = SystemWebSocketMessage(
                messageId = testMessageId,
                timestamp = pastTime,
                sessionId = testSessionId,
                content = "Past message"
            )

            Then("과거 타임스탬프도 허용되어야 한다") {
                message.timestamp shouldBe pastTime
                message.timestamp shouldBeLessThan System.currentTimeMillis()
            }
        }
    }

    Given("메시지 ID 테스트 시") {
        When("null 메시지 ID로 메시지를 생성하면") {
            val message = HeartbeatWebSocketMessage(
                messageId = null,
                timestamp = testTimestamp,
                sessionId = testSessionId
            )

            Then("null 메시지 ID가 허용되어야 한다") {
                message.messageId shouldBe null
            }
        }

        When("빈 문자열 메시지 ID로 메시지를 생성하면") {
            val message = HeartbeatWebSocketMessage(
                messageId = "",
                timestamp = testTimestamp,
                sessionId = testSessionId
            )

            Then("빈 문자열 메시지 ID가 허용되어야 한다") {
                message.messageId shouldBe ""
            }
        }

        When("긴 메시지 ID로 메시지를 생성하면") {
            val longMessageId = "msg_" + "a".repeat(100)
            val message = ChatWebSocketMessage(
                messageId = longMessageId,
                timestamp = testTimestamp,
                sessionId = testSessionId,
                content = "Long ID message",
                userId = 100L,
                roomId = 1L,
                userNickname = "LongIdUser"
            )

            Then("긴 메시지 ID가 허용되어야 한다") {
                message.messageId shouldBe longMessageId
                message.messageId?.length shouldBe 104
            }
        }
    }

    Given("세션 ID 테스트 시") {
        When("다양한 형식의 세션 ID로 메시지를 생성하면") {
            val sessionIds = listOf(
                "session-123",
                "sess_456_789",
                "uuid-12345678-1234-1234-1234-123456789012",
                "short",
                "session.with.dots",
                null
            )

            sessionIds.forEach { sessionId ->
                val message = HeartbeatWebSocketMessage(
                    messageId = testMessageId,
                    timestamp = testTimestamp,
                    sessionId = sessionId
                )

                Then("세션 ID '$sessionId'가 허용되어야 한다") {
                    message.sessionId shouldBe sessionId
                }
            }
        }
    }

    Given("사용자 ID 및 룸 ID 테스트 시") {
        When("다양한 사용자 ID와 룸 ID로 메시지를 생성하면") {
            val testCases = listOf(
                Pair(1L, 1L),
                Pair(0L, 0L),
                Pair(Long.MAX_VALUE, Long.MAX_VALUE),
                Pair(-1L, -1L)
            )

            testCases.forEach { (userId, roomId) ->
                val message = ChatWebSocketMessage(
                    messageId = testMessageId,
                    timestamp = testTimestamp,
                    sessionId = testSessionId,
                    content = "Test message",
                    userId = userId,
                    roomId = roomId,
                    userNickname = "TestUser"
                )

                Then("사용자 ID ${userId}와 룸 ID ${roomId}가 허용되어야 한다") {
                    message.userId shouldBe userId
                    message.roomId shouldBe roomId
                }
            }
        }
    }
})