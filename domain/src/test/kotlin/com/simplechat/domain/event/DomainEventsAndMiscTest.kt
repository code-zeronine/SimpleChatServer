package com.simplechat.domain.event

import com.simplechat.domain.entity.ChatMessage
import com.simplechat.domain.entity.MessageType
import com.simplechat.domain.message.MessageTarget
import com.simplechat.domain.message.websocket.DuplicateLoginAction
import com.simplechat.domain.message.websocket.DuplicateLoginInfo
import com.simplechat.domain.message.websocket.DuplicateLoginWebSocketMessage
import com.simplechat.domain.message.websocket.WebSocketMessage
import com.simplechat.domain.message.websocket.WebSocketMessageType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import java.time.Instant
import java.time.LocalDateTime

/**
 * 도메인 이벤트 및 기타 도메인 클래스들에 대한 테스트
 * 
 * 테스트 대상:
 * - MessageSavedEvent (도메인 이벤트)
 * - MessageTarget sealed class
 * - DuplicateLoginInfo data class
 * - DuplicateLoginWebSocketMessage
 * - 기타 도메인 관련 클래스들
 */
class DomainEventsAndMiscTest : BehaviorSpec({

    Given("MessageSavedEvent 도메인 이벤트 테스트") {
        val testMessage = ChatMessage(
            id = "test-message-123",
            roomId = 1L,
            userId = 100L,
            content = "테스트 메시지",
            timestamp = LocalDateTime.now(),
            messageType = MessageType.TEXT
        )

        When("기본 생성자로 이벤트를 생성하면") {
            val event = MessageSavedEvent(message = testMessage)

            Then("메시지와 자동 생성된 시간을 가져야 한다") {
                event.message shouldBe testMessage
                event.message shouldBeSameInstanceAs testMessage
                event.occurredAt.shouldBeInstanceOf<Instant>()
                event.occurredAt shouldBeLessThan Instant.now()
                event.occurredAt shouldNotBe null
            }
        }

        When("명시적 시간으로 이벤트를 생성하면") {
            val specificTime = Instant.now().minusSeconds(3600)
            val event = MessageSavedEvent(
                message = testMessage,
                occurredAt = specificTime
            )

            Then("지정한 시간이 설정되어야 한다") {
                event.message shouldBe testMessage
                event.occurredAt shouldBe specificTime
            }
        }

        When("이벤트의 데이터 클래스 특성을 테스트하면") {
            val event1 = MessageSavedEvent(message = testMessage)
            val event2 = MessageSavedEvent(
                message = testMessage,
                occurredAt = event1.occurredAt
            )

            Then("동일한 내용이면 equals()가 true여야 한다") {
                event1 shouldBe event2
            }

            Then("copy()가 올바르게 동작해야 한다") {
                val newTime = Instant.now().plusSeconds(100)
                val copiedEvent = event1.copy(occurredAt = newTime)
                
                copiedEvent.message shouldBe event1.message
                copiedEvent.occurredAt shouldBe newTime
                copiedEvent.occurredAt shouldNotBe event1.occurredAt
            }

            Then("toString()이 모든 속성을 포함해야 한다") {
                val eventString = event1.toString()
                eventString shouldContain "MessageSavedEvent"
                eventString shouldContain "test-message-123"
                eventString shouldContain "occurredAt"
            }
        }
    }

    Given("MessageTarget sealed class 테스트") {
        When("Session 타겟을 생성하면") {
            val sessionTarget = MessageTarget.Session("session-123")

            Then("올바른 세션 ID를 가져야 한다") {
                sessionTarget.sessionId shouldBe "session-123"
                sessionTarget.shouldBeInstanceOf<MessageTarget.Session>()
                sessionTarget.shouldBeInstanceOf<MessageTarget>()
            }
        }

        When("User 타겟을 생성하면") {
            val userTarget = MessageTarget.User(userId = 456L)

            Then("올바른 사용자 ID를 가져야 한다") {
                userTarget.userId shouldBe 456L
                userTarget.shouldBeInstanceOf<MessageTarget.User>()
                userTarget.shouldBeInstanceOf<MessageTarget>()
            }
        }

        When("Users 타겟을 생성하면") {
            val userIds = setOf(100L, 200L, 300L)
            val usersTarget = MessageTarget.Users(userIds = userIds)

            Then("올바른 사용자 ID 집합을 가져야 한다") {
                usersTarget.userIds shouldBe userIds
                usersTarget.userIds shouldHaveSize 3
                usersTarget.userIds shouldContain 100L
                usersTarget.userIds shouldContain 200L
                usersTarget.userIds shouldContain 300L
                usersTarget.shouldBeInstanceOf<MessageTarget.Users>()
            }
        }

        When("Room 타겟을 기본값으로 생성하면") {
            val roomTarget = MessageTarget.Room(chatRoomId = "room-789")

            Then("채팅방 ID와 기본 제외 세션 값을 가져야 한다") {
                roomTarget.chatRoomId shouldBe "room-789"
                roomTarget.excludeSessionId shouldBe null
                roomTarget.shouldBeInstanceOf<MessageTarget.Room>()
            }
        }

        When("Room 타겟을 제외 세션과 함께 생성하면") {
            val roomTarget = MessageTarget.Room(
                chatRoomId = "room-789",
                excludeSessionId = "exclude-session-123"
            )

            Then("채팅방 ID와 제외 세션 ID를 가져야 한다") {
                roomTarget.chatRoomId shouldBe "room-789"
                roomTarget.excludeSessionId shouldBe "exclude-session-123"
            }
        }

        When("Global 타겟을 사용하면") {
            val globalTarget = MessageTarget.Global

            Then("객체 인스턴스여야 한다") {
                globalTarget.shouldBeInstanceOf<MessageTarget.Global>()
                globalTarget.shouldBeInstanceOf<MessageTarget>()
            }
        }

        When("서로 다른 타입의 타겟들을 비교하면") {
            val targets = listOf(
                MessageTarget.Session("session-1"),
                MessageTarget.User(100L),
                MessageTarget.Users(setOf(200L, 300L)),
                MessageTarget.Room("room-1"),
                MessageTarget.Global
            )

            Then("각각이 다른 타입이어야 한다") {
                targets.forEach { target ->
                    target.shouldBeInstanceOf<MessageTarget>()
                }
                
                targets[0].shouldBeInstanceOf<MessageTarget.Session>()
                targets[1].shouldBeInstanceOf<MessageTarget.User>()
                targets[2].shouldBeInstanceOf<MessageTarget.Users>()
                targets[3].shouldBeInstanceOf<MessageTarget.Room>()
                targets[4].shouldBeInstanceOf<MessageTarget.Global>()
            }
        }

        When("데이터 클래스 특성을 테스트하면") {
            val user1 = MessageTarget.User(123L)
            val user2 = MessageTarget.User(123L)
            val user3 = MessageTarget.User(456L)

            Then("동일한 값이면 equals()가 true여야 한다") {
                user1 shouldBe user2
                user1 shouldNotBe user3
            }

            Then("copy()가 올바르게 동작해야 한다") {
                val copiedUser = user1.copy(userId = 999L)
                copiedUser.userId shouldBe 999L
                copiedUser shouldNotBe user1
            }
        }
    }

    Given("DuplicateLoginInfo data class 테스트") {
        val loginTime = Instant.now()

        When("기본값으로 생성하면") {
            val info = DuplicateLoginInfo(
                loginTime = loginTime,
                ipAddress = "192.168.1.100",
                userAgent = "Mozilla/5.0"
            )

            Then("기본값들이 설정되어야 한다") {
                info.loginTime shouldBe loginTime
                info.ipAddress shouldBe "192.168.1.100"
                info.userAgent shouldBe "Mozilla/5.0"
                info.deviceInfo shouldBe null
                info.location shouldBe null
            }
        }

        When("모든 필드를 설정하여 생성하면") {
            val info = DuplicateLoginInfo(
                loginTime = loginTime,
                ipAddress = "10.0.0.1",
                userAgent = "Chrome/91.0",
                deviceInfo = "iPhone 12",
                location = "Seoul, Korea"
            )

            Then("모든 필드가 올바르게 설정되어야 한다") {
                info.loginTime shouldBe loginTime
                info.ipAddress shouldBe "10.0.0.1"
                info.userAgent shouldBe "Chrome/91.0"
                info.deviceInfo shouldBe "iPhone 12"
                info.location shouldBe "Seoul, Korea"
            }
        }

        When("null 값들로 생성하면") {
            val info = DuplicateLoginInfo(
                loginTime = loginTime,
                ipAddress = null,
                userAgent = null,
                deviceInfo = null,
                location = null
            )

            Then("null 값들이 허용되어야 한다") {
                info.loginTime shouldBe loginTime
                info.ipAddress shouldBe null
                info.userAgent shouldBe null
                info.deviceInfo shouldBe null
                info.location shouldBe null
            }
        }

        When("데이터 클래스 특성을 테스트하면") {
            val info1 = DuplicateLoginInfo(
                loginTime = loginTime,
                ipAddress = "192.168.1.1",
                userAgent = "Safari"
            )
            val info2 = DuplicateLoginInfo(
                loginTime = loginTime,
                ipAddress = "192.168.1.1",
                userAgent = "Safari"
            )

            Then("동일한 값이면 equals()가 true여야 한다") {
                info1 shouldBe info2
            }

            Then("copy()가 올바르게 동작해야 한다") {
                val copiedInfo = info1.copy(
                    ipAddress = "10.0.0.2",
                    deviceInfo = "Android Phone"
                )
                
                copiedInfo.loginTime shouldBe info1.loginTime
                copiedInfo.ipAddress shouldBe "10.0.0.2"
                copiedInfo.userAgent shouldBe info1.userAgent
                copiedInfo.deviceInfo shouldBe "Android Phone"
                copiedInfo.location shouldBe null
            }
        }
    }

    Given("DuplicateLoginWebSocketMessage 테스트") {
        val testLoginInfo = DuplicateLoginInfo(
            loginTime = Instant.now(),
            ipAddress = "192.168.1.50",
            userAgent = "Chrome/91.0",
            deviceInfo = "Windows PC",
            location = "Seoul"
        )

        When("기본값으로 메시지를 생성하면") {
            val message = DuplicateLoginWebSocketMessage(
                newLoginInfo = testLoginInfo
            )

            Then("기본값들이 올바르게 설정되어야 한다") {
                message.type shouldBe WebSocketMessageType.DUPLICATE_LOGIN_DETECTED
                message.messageId shouldStartWith "duplicate-login-"
                message.timestamp shouldBeGreaterThan 0L
                message.sessionId shouldBe null
                message.message shouldBe "다른 위치에서 로그인이 감지되었습니다."
                message.newLoginInfo shouldBe testLoginInfo
                message.action shouldBe DuplicateLoginAction.NOTIFY_ONLY
                message.shouldBeInstanceOf<WebSocketMessage>()
            }
        }

        When("모든 값을 명시적으로 설정하여 메시지를 생성하면") {
            val customMessageId = "custom-duplicate-login-001"
            val customTimestamp = System.currentTimeMillis()
            val customSessionId = "session-xyz"
            val customMessage = "새로운 로그인이 감지되어 기존 세션을 종료합니다."
            val customAction = DuplicateLoginAction.FORCE_LOGOUT_OTHERS

            val message = DuplicateLoginWebSocketMessage(
                messageId = customMessageId,
                timestamp = customTimestamp,
                sessionId = customSessionId,
                message = customMessage,
                newLoginInfo = testLoginInfo,
                action = customAction
            )

            Then("설정한 값들이 정확히 반영되어야 한다") {
                message.messageId shouldBe customMessageId
                message.timestamp shouldBe customTimestamp
                message.sessionId shouldBe customSessionId
                message.message shouldBe customMessage
                message.newLoginInfo shouldBe testLoginInfo
                message.action shouldBe customAction
                message.type shouldBe WebSocketMessageType.DUPLICATE_LOGIN_DETECTED
            }
        }

        When("다양한 행동 옵션으로 메시지를 생성하면") {
            val actions = DuplicateLoginAction.entries
            
            Then("각 행동에 대해 메시지가 올바르게 생성되어야 한다") {
                actions.forEach { action ->
                    val message = DuplicateLoginWebSocketMessage(
                        newLoginInfo = testLoginInfo,
                        action = action
                    )
                    
                    message.action shouldBe action
                    message.type shouldBe WebSocketMessageType.DUPLICATE_LOGIN_DETECTED
                    message.newLoginInfo shouldBe testLoginInfo
                }
            }
        }

        When("데이터 클래스 특성을 테스트하면") {
            val message1 = DuplicateLoginWebSocketMessage(
                messageId = "test-001",
                timestamp = 1234567890L,
                sessionId = "session-123",
                message = "Test message",
                newLoginInfo = testLoginInfo,
                action = DuplicateLoginAction.ASK_USER_CHOICE
            )
            
            val message2 = DuplicateLoginWebSocketMessage(
                messageId = "test-001",
                timestamp = 1234567890L,
                sessionId = "session-123",
                message = "Test message",
                newLoginInfo = testLoginInfo,
                action = DuplicateLoginAction.ASK_USER_CHOICE
            )

            Then("동일한 값이면 equals()가 true여야 한다") {
                message1 shouldBe message2
            }

            Then("copy()가 올바르게 동작해야 한다") {
                val copiedMessage = message1.copy(
                    action = DuplicateLoginAction.FORCE_LOGOUT_CURRENT,
                    message = "Modified message"
                )
                
                copiedMessage.messageId shouldBe message1.messageId
                copiedMessage.timestamp shouldBe message1.timestamp
                copiedMessage.sessionId shouldBe message1.sessionId
                copiedMessage.message shouldBe "Modified message"
                copiedMessage.newLoginInfo shouldBe message1.newLoginInfo
                copiedMessage.action shouldBe DuplicateLoginAction.FORCE_LOGOUT_CURRENT
                copiedMessage.type shouldBe WebSocketMessageType.DUPLICATE_LOGIN_DETECTED
            }

            Then("toString()이 모든 속성을 포함해야 한다") {
                val messageString = message1.toString()
                messageString shouldContain "DuplicateLoginWebSocketMessage"
                messageString shouldContain "test-001"
                messageString shouldContain "ASK_USER_CHOICE"
            }
        }
    }

    Given("도메인 클래스들 간의 통합 테스트") {
        When("도메인 이벤트와 메시지가 함께 사용되면") {
            val chatMessage = ChatMessage(
                id = "integration-test-msg",
                roomId = 42L,
                userId = 999L,
                content = "통합 테스트 메시지",
                timestamp = LocalDateTime.now(),
                messageType = MessageType.TEXT
            )
            
            val messageSavedEvent = MessageSavedEvent(message = chatMessage)
            val messageTarget = MessageTarget.Room(
                chatRoomId = chatMessage.roomId.toString(),
                excludeSessionId = "current-session"
            )

            Then("서로 연관된 데이터가 일관성을 가져야 한다") {
                messageSavedEvent.message shouldBe chatMessage
                messageTarget.chatRoomId shouldBe chatMessage.roomId.toString()
                messageTarget.excludeSessionId shouldBe "current-session"
            }
        }

        When("WebSocket 메시지들의 타입 체계를 확인하면") {
            val duplicateLoginMessage = DuplicateLoginWebSocketMessage(
                newLoginInfo = DuplicateLoginInfo(
                    loginTime = Instant.now(),
                    ipAddress = "test-ip",
                    userAgent = "test-agent"
                )
            )

            Then("올바른 상속 관계를 가져야 한다") {
                duplicateLoginMessage.shouldBeInstanceOf<WebSocketMessage>()
                duplicateLoginMessage.type shouldBe WebSocketMessageType.DUPLICATE_LOGIN_DETECTED
                duplicateLoginMessage.messageId shouldNotBe null
                duplicateLoginMessage.timestamp shouldBeGreaterThan 0L
            }
        }

        When("다양한 MessageTarget 타입들을 컬렉션으로 처리하면") {
            val targets: List<MessageTarget> = listOf(
                MessageTarget.Session("session-1"),
                MessageTarget.User(100L),
                MessageTarget.Users(setOf(200L, 300L, 400L)),
                MessageTarget.Room("room-1", "exclude-session"),
                MessageTarget.Global
            )

            Then("sealed class의 다형성이 올바르게 동작해야 한다") {
                targets shouldHaveSize 5
                targets.forEach { target ->
                    target.shouldBeInstanceOf<MessageTarget>()
                }

                // when expression으로 타입 확인 가능
                targets.forEach { target ->
                    when (target) {
                        is MessageTarget.Session -> target.sessionId shouldNotBe null
                        is MessageTarget.User -> target.userId shouldBeGreaterThan 0L
                        is MessageTarget.Users -> target.userIds.size shouldBeGreaterThan 0
                        is MessageTarget.Room -> target.chatRoomId shouldNotBe null
                        is MessageTarget.Global -> target.shouldBeInstanceOf<MessageTarget.Global>()
                    }
                }
            }
        }
    }
})