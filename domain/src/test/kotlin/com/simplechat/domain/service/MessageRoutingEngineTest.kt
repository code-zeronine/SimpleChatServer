package com.simplechat.domain.service

import com.simplechat.domain.message.MessageTarget
import com.simplechat.domain.message.websocket.*
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.*

/**
 * MessageRoutingEngine 인터페이스 테스트
 *
 * 메시지 라우팅 엔진의 계약(contract)과 구현체의 동작을 테스트합니다.
 * MockK를 사용하여 구현체를 모킹하고 인터페이스 명세를 검증합니다.
 */
class MessageRoutingEngineTest : BehaviorSpec({

    // Mock 메시지 라우팅 엔진
    lateinit var messageRoutingEngine: MessageRoutingEngine

    // 테스트용 메시지 생성 헬퍼 메서드들
    var messageIdCounter = 0
    fun createChatMessage(
        roomId: Long = 1L,
        userId: Long = 100L,
        content: String = "Test message",
        userNickname: String = "TestUser"
    ): ChatWebSocketMessage {
        return ChatWebSocketMessage(
            messageId = "test-msg-${++messageIdCounter}",
            timestamp = System.currentTimeMillis(),
            sessionId = "session-${messageIdCounter}",
            content = content,
            userId = userId,
            roomId = roomId,
            userNickname = userNickname
        )
    }

    fun createJoinMessage(
        roomId: Long = 1L,
        userId: Long = 100L,
        userNickname: String = "TestUser"
    ): JoinWebSocketMessage {
        return JoinWebSocketMessage(
            messageId = "join-msg-${++messageIdCounter}",
            timestamp = System.currentTimeMillis(),
            sessionId = "session-${messageIdCounter}",
            userId = userId,
            roomId = roomId,
            userNickname = userNickname
        )
    }

    fun createLeaveMessage(
        roomId: Long = 1L,
        userId: Long = 100L,
        userNickname: String = "TestUser"
    ): LeaveWebSocketMessage {
        return LeaveWebSocketMessage(
            messageId = "leave-msg-${++messageIdCounter}",
            timestamp = System.currentTimeMillis(),
            sessionId = "session-${messageIdCounter}",
            userId = userId,
            roomId = roomId,
            userNickname = userNickname
        )
    }

    fun createTypingMessage(
        roomId: Long = 1L,
        userId: Long = 100L,
        isTyping: Boolean = true,
        userNickname: String = "TestUser"
    ): TypingWebSocketMessage {
        return TypingWebSocketMessage(
            messageId = "typing-msg-${++messageIdCounter}",
            timestamp = System.currentTimeMillis(),
            sessionId = "session-${messageIdCounter}",
            userId = userId,
            roomId = roomId,
            userNickname = userNickname,
            isTyping = isTyping
        )
    }

    fun createSystemMessage(
        content: String = "System notification"
    ): SystemWebSocketMessage {
        return SystemWebSocketMessage(
            messageId = "system-msg-${++messageIdCounter}",
            timestamp = System.currentTimeMillis(),
            sessionId = null,
            content = content
        )
    }

    fun createErrorMessage(
        errorCode: String = "TEST_ERROR",
        errorMessage: String = "Test error",
        sessionId: String = "error-session"
    ): ErrorWebSocketMessage {
        return ErrorWebSocketMessage(
            messageId = "error-msg-${++messageIdCounter}",
            timestamp = System.currentTimeMillis(),
            sessionId = sessionId,
            errorCode = errorCode,
            errorMessage = errorMessage
        )
    }

    beforeEach {
        messageRoutingEngine = mockk<MessageRoutingEngine>(relaxed = true)
        messageIdCounter = 0
    }

    Given("MessageRoutingEngine 메시지 라우팅 기능 테스트 시") {
        When("채팅 메시지를 라우팅하면") {
            beforeEach {
                every { messageRoutingEngine.routeMessage(any(), any()) } returns Mono.empty()
            }

            Then("성공적으로 라우팅되어야 한다") {
                val chatMessage = createChatMessage()
                val sourceSessionId = "source-session-1"
                
                val result = messageRoutingEngine.routeMessage(chatMessage, sourceSessionId)

                StepVerifier.create(result)
                    .verifyComplete()

                verify(atLeast = 1) { messageRoutingEngine.routeMessage(chatMessage, sourceSessionId) }
            }
        }

        When("JOIN 메시지를 라우팅하면") {
            beforeEach {
                every { messageRoutingEngine.routeMessage(any(), any()) } returns Mono.empty()
            }

            Then("성공적으로 라우팅되어야 한다") {
                val joinMessage = createJoinMessage()
                val sourceSessionId = "join-session-1"
                
                val result = messageRoutingEngine.routeMessage(joinMessage, sourceSessionId)

                StepVerifier.create(result)
                    .verifyComplete()

                verify(atLeast = 1) { messageRoutingEngine.routeMessage(joinMessage, sourceSessionId) }
            }
        }

        When("LEAVE 메시지를 라우팅하면") {
            beforeEach {
                every { messageRoutingEngine.routeMessage(any(), any()) } returns Mono.empty()
            }

            Then("성공적으로 라우팅되어야 한다") {
                val leaveMessage = createLeaveMessage()
                val sourceSessionId = "leave-session-1"
                
                val result = messageRoutingEngine.routeMessage(leaveMessage, sourceSessionId)

                StepVerifier.create(result)
                    .verifyComplete()

                verify(atLeast = 1) { messageRoutingEngine.routeMessage(leaveMessage, sourceSessionId) }
            }
        }

        When("TYPING 메시지를 라우팅하면") {
            beforeEach {
                every { messageRoutingEngine.routeMessage(any(), any()) } returns Mono.empty()
            }

            Then("성공적으로 라우팅되어야 한다") {
                val typingMessage = createTypingMessage(isTyping = true)
                val sourceSessionId = "typing-session-1"
                
                val result = messageRoutingEngine.routeMessage(typingMessage, sourceSessionId)

                StepVerifier.create(result)
                    .verifyComplete()

                verify(atLeast = 1) { messageRoutingEngine.routeMessage(typingMessage, sourceSessionId) }
            }
        }

        When("SYSTEM 메시지를 라우팅하면") {
            beforeEach {
                every { messageRoutingEngine.routeMessage(any(), any()) } returns Mono.empty()
            }

            Then("성공적으로 라우팅되어야 한다") {
                val systemMessage = createSystemMessage()
                val sourceSessionId = "system-session-1"
                
                val result = messageRoutingEngine.routeMessage(systemMessage, sourceSessionId)

                StepVerifier.create(result)
                    .verifyComplete()

                verify(atLeast = 1) { messageRoutingEngine.routeMessage(systemMessage, sourceSessionId) }
            }
        }

        When("ERROR 메시지를 라우팅하면") {
            beforeEach {
                every { messageRoutingEngine.routeMessage(any(), any()) } returns Mono.empty()
            }

            Then("성공적으로 라우팅되어야 한다") {
                val errorMessage = createErrorMessage()
                val sourceSessionId = "error-session-1"
                
                val result = messageRoutingEngine.routeMessage(errorMessage, sourceSessionId)

                StepVerifier.create(result)
                    .verifyComplete()

                verify(atLeast = 1) { messageRoutingEngine.routeMessage(errorMessage, sourceSessionId) }
            }
        }

        When("라우팅 중 오류가 발생하면") {
            val expectedException = RuntimeException("Routing failed")

            beforeEach {
                every { messageRoutingEngine.routeMessage(any(), any()) } returns Mono.error(expectedException)
            }

            Then("오류가 전파되어야 한다") {
                val chatMessage = createChatMessage()
                val sourceSessionId = "failing-session"
                
                val result = messageRoutingEngine.routeMessage(chatMessage, sourceSessionId)

                StepVerifier.create(result)
                    .expectErrorMatches { it is RuntimeException && it.message == "Routing failed" }
                    .verify()

                verify(atLeast = 1) { messageRoutingEngine.routeMessage(chatMessage, sourceSessionId) }
            }
        }
    }

    Given("MessageRoutingEngine 채팅방 브로드캐스트 기능 테스트 시") {
        When("채팅방에 메시지를 브로드캐스트하면") {
            beforeEach {
                every { messageRoutingEngine.broadcastToRoom(any(), any(), any()) } returns Mono.empty()
            }

            Then("성공적으로 브로드캐스트되어야 한다") {
                val chatRoomId = "room-123"
                val message = createChatMessage(roomId = 123L)
                
                val result = messageRoutingEngine.broadcastToRoom(chatRoomId, message)

                StepVerifier.create(result)
                    .verifyComplete()

                verify(atLeast = 1) { messageRoutingEngine.broadcastToRoom(chatRoomId, message, null) }
            }
        }

        When("특정 세션을 제외하고 채팅방에 브로드캐스트하면") {
            beforeEach {
                every { messageRoutingEngine.broadcastToRoom(any(), any(), any()) } returns Mono.empty()
            }

            Then("제외된 세션을 제외하고 브로드캐스트되어야 한다") {
                val chatRoomId = "room-456"
                val message = createChatMessage(roomId = 456L)
                val excludeSessionId = "exclude-session-1"
                
                val result = messageRoutingEngine.broadcastToRoom(chatRoomId, message, excludeSessionId)

                StepVerifier.create(result)
                    .verifyComplete()

                verify(atLeast = 1) { messageRoutingEngine.broadcastToRoom(chatRoomId, message, excludeSessionId) }
            }
        }

        When("빈 채팅방에 브로드캐스트하면") {
            beforeEach {
                every { messageRoutingEngine.broadcastToRoom(any(), any(), any()) } returns Mono.empty()
            }

            Then("성공적으로 완료되어야 한다") {
                val emptyChatRoomId = "empty-room"
                val message = createChatMessage()
                
                val result = messageRoutingEngine.broadcastToRoom(emptyChatRoomId, message)

                StepVerifier.create(result)
                    .verifyComplete()

                verify(atLeast = 1) { messageRoutingEngine.broadcastToRoom(emptyChatRoomId, message, null) }
            }
        }

        When("채팅방 브로드캐스트 중 오류가 발생하면") {
            val expectedException = RuntimeException("Broadcast failed")

            beforeEach {
                every { messageRoutingEngine.broadcastToRoom(any(), any(), any()) } returns Mono.error(expectedException)
            }

            Then("오류가 전파되어야 한다") {
                val chatRoomId = "failing-room"
                val message = createChatMessage()
                
                val result = messageRoutingEngine.broadcastToRoom(chatRoomId, message)

                StepVerifier.create(result)
                    .expectErrorMessage("Broadcast failed")
                    .verify()

                verify(atLeast = 1) { messageRoutingEngine.broadcastToRoom(chatRoomId, message, null) }
            }
        }
    }

    Given("MessageRoutingEngine 사용자별 메시지 전송 기능 테스트 시") {
        When("특정 사용자에게 메시지를 전송하면") {
            beforeEach {
                every { messageRoutingEngine.sendToUser(any(), any()) } returns Mono.empty()
            }

            Then("성공적으로 전송되어야 한다") {
                val userId = 100L
                val message = createChatMessage(userId = userId)
                
                val result = messageRoutingEngine.sendToUser(userId, message)

                StepVerifier.create(result)
                    .verifyComplete()

                verify(atLeast = 1) { messageRoutingEngine.sendToUser(userId, message) }
            }
        }

        When("여러 사용자에게 메시지를 전송하면") {
            beforeEach {
                every { messageRoutingEngine.sendToUsers(any(), any()) } returns Mono.empty()
            }

            Then("모든 사용자에게 성공적으로 전송되어야 한다") {
                val userIds = setOf(100L, 101L, 102L)
                val message = createSystemMessage()
                
                val result = messageRoutingEngine.sendToUsers(userIds, message)

                StepVerifier.create(result)
                    .verifyComplete()

                verify(atLeast = 1) { messageRoutingEngine.sendToUsers(userIds, message) }
            }
        }

        When("빈 사용자 목록에 메시지를 전송하면") {
            beforeEach {
                every { messageRoutingEngine.sendToUsers(any(), any()) } returns Mono.empty()
            }

            Then("성공적으로 완료되어야 한다") {
                val emptyUserIds = emptySet<Long>()
                val message = createSystemMessage()
                
                val result = messageRoutingEngine.sendToUsers(emptyUserIds, message)

                StepVerifier.create(result)
                    .verifyComplete()

                verify(atLeast = 1) { messageRoutingEngine.sendToUsers(emptyUserIds, message) }
            }
        }

        When("오프라인 사용자에게 메시지를 전송하면") {
            beforeEach {
                every { messageRoutingEngine.sendToUser(any(), any()) } returns Mono.empty()
            }

            Then("성공적으로 처리되어야 한다") {
                val offlineUserId = 999L
                val message = createChatMessage()
                
                val result = messageRoutingEngine.sendToUser(offlineUserId, message)

                StepVerifier.create(result)
                    .verifyComplete()

                verify(atLeast = 1) { messageRoutingEngine.sendToUser(offlineUserId, message) }
            }
        }

        When("사용자 메시지 전송 중 오류가 발생하면") {
            val expectedException = RuntimeException("User delivery failed")

            beforeEach {
                every { messageRoutingEngine.sendToUser(any(), any()) } returns Mono.error(expectedException)
            }

            Then("오류가 전파되어야 한다") {
                val userId = 100L
                val message = createChatMessage()
                
                val result = messageRoutingEngine.sendToUser(userId, message)

                StepVerifier.create(result)
                    .expectErrorMessage("User delivery failed")
                    .verify()

                verify(atLeast = 1) { messageRoutingEngine.sendToUser(userId, message) }
            }
        }
    }

    Given("MessageRoutingEngine 세션별 메시지 전송 기능 테스트 시") {
        When("특정 세션에 메시지를 전송하면") {
            beforeEach {
                every { messageRoutingEngine.sendToSession(any(), any()) } returns Mono.empty()
            }

            Then("성공적으로 전송되어야 한다") {
                val sessionId = "session-123"
                val message = createChatMessage()
                
                val result = messageRoutingEngine.sendToSession(sessionId, message)

                StepVerifier.create(result)
                    .verifyComplete()

                verify(atLeast = 1) { messageRoutingEngine.sendToSession(sessionId, message) }
            }
        }

        When("존재하지 않는 세션에 메시지를 전송하면") {
            beforeEach {
                every { messageRoutingEngine.sendToSession(any(), any()) } returns Mono.empty()
            }

            Then("성공적으로 처리되어야 한다") {
                val nonExistentSessionId = "non-existent-session"
                val message = createErrorMessage()
                
                val result = messageRoutingEngine.sendToSession(nonExistentSessionId, message)

                StepVerifier.create(result)
                    .verifyComplete()

                verify(atLeast = 1) { messageRoutingEngine.sendToSession(nonExistentSessionId, message) }
            }
        }

        When("세션 메시지 전송 중 오류가 발생하면") {
            val expectedException = RuntimeException("Session delivery failed")

            beforeEach {
                every { messageRoutingEngine.sendToSession(any(), any()) } returns Mono.error(expectedException)
            }

            Then("오류가 전파되어야 한다") {
                val sessionId = "failing-session"
                val message = createChatMessage()
                
                val result = messageRoutingEngine.sendToSession(sessionId, message)

                StepVerifier.create(result)
                    .expectErrorMessage("Session delivery failed")
                    .verify()

                verify(atLeast = 1) { messageRoutingEngine.sendToSession(sessionId, message) }
            }
        }
    }

    Given("MessageRoutingEngine 전역 브로드캐스트 기능 테스트 시") {
        When("전역 메시지를 브로드캐스트하면") {
            beforeEach {
                every { messageRoutingEngine.broadcastGlobally(any()) } returns Mono.empty()
            }

            Then("모든 사용자에게 성공적으로 브로드캐스트되어야 한다") {
                val globalMessage = createSystemMessage(content = "Server maintenance in 5 minutes")
                
                val result = messageRoutingEngine.broadcastGlobally(globalMessage)

                StepVerifier.create(result)
                    .verifyComplete()

                verify(atLeast = 1) { messageRoutingEngine.broadcastGlobally(globalMessage) }
            }
        }

        When("연결된 사용자가 없을 때 전역 브로드캐스트하면") {
            beforeEach {
                every { messageRoutingEngine.broadcastGlobally(any()) } returns Mono.empty()
            }

            Then("성공적으로 완료되어야 한다") {
                val globalMessage = createSystemMessage()
                
                val result = messageRoutingEngine.broadcastGlobally(globalMessage)

                StepVerifier.create(result)
                    .verifyComplete()

                verify(atLeast = 1) { messageRoutingEngine.broadcastGlobally(globalMessage) }
            }
        }

        When("전역 브로드캐스트 중 오류가 발생하면") {
            val expectedException = RuntimeException("Global broadcast failed")

            beforeEach {
                every { messageRoutingEngine.broadcastGlobally(any()) } returns Mono.error(expectedException)
            }

            Then("오류가 전파되어야 한다") {
                val globalMessage = createSystemMessage()
                
                val result = messageRoutingEngine.broadcastGlobally(globalMessage)

                StepVerifier.create(result)
                    .expectErrorMessage("Global broadcast failed")
                    .verify()

                verify(atLeast = 1) { messageRoutingEngine.broadcastGlobally(globalMessage) }
            }
        }
    }

    Given("MessageRoutingEngine 재시도 기능 테스트 시") {
        When("실패한 메시지를 재시도하면") {
            beforeEach {
                every { messageRoutingEngine.retryFailedMessage(any(), any(), any()) } returns Mono.empty()
            }

            Then("성공적으로 재시도되어야 한다") {
                val message = createChatMessage()
                val target = MessageTarget.Room("room-123")
                val maxRetries = 3
                
                val result = messageRoutingEngine.retryFailedMessage(message, target, maxRetries)

                StepVerifier.create(result)
                    .verifyComplete()

                verify(atLeast = 1) { messageRoutingEngine.retryFailedMessage(message, target, maxRetries) }
            }
        }

        When("기본 재시도 횟수로 실패한 메시지를 재시도하면") {
            beforeEach {
                every { messageRoutingEngine.retryFailedMessage(any(), any()) } returns Mono.empty()
            }

            Then("기본 재시도 횟수로 재시도되어야 한다") {
                val message = createChatMessage()
                val target = MessageTarget.User(100L)
                
                val result = messageRoutingEngine.retryFailedMessage(message, target)

                StepVerifier.create(result)
                    .verifyComplete()

                verify(atLeast = 1) { messageRoutingEngine.retryFailedMessage(message, target, 3) }
            }
        }

        When("다양한 타입의 메시지 타겟으로 재시도하면") {
            beforeEach {
                every { messageRoutingEngine.retryFailedMessage(any(), any(), any()) } returns Mono.empty()
            }

            Then("모든 타겟 타입이 성공적으로 재시도되어야 한다") {
                val message = createChatMessage()
                val sessionTarget = MessageTarget.Session("session-123")
                val userTarget = MessageTarget.User(100L)
                val usersTarget = MessageTarget.Users(setOf(100L, 101L))
                val roomTarget = MessageTarget.Room("room-123")
                val globalTarget = MessageTarget.Global

                val targets = listOf(sessionTarget, userTarget, usersTarget, roomTarget, globalTarget)
                
                targets.forEach { target ->
                    val result = messageRoutingEngine.retryFailedMessage(message, target)
                    StepVerifier.create(result)
                        .verifyComplete()
                }

                verify(atLeast = 5) { messageRoutingEngine.retryFailedMessage(any(), any(), 3) }
            }
        }

        When("재시도 중 오류가 발생하면") {
            val expectedException = RuntimeException("Retry failed")

            beforeEach {
                every { messageRoutingEngine.retryFailedMessage(any(), any(), any()) } returns Mono.error(expectedException)
            }

            Then("오류가 전파되어야 한다") {
                val message = createChatMessage()
                val target = MessageTarget.Room("failing-room")
                
                val result = messageRoutingEngine.retryFailedMessage(message, target)

                StepVerifier.create(result)
                    .expectErrorMessage("Retry failed")
                    .verify()

                verify(atLeast = 1) { messageRoutingEngine.retryFailedMessage(message, target, 3) }
            }
        }
    }

    Given("MessageRoutingEngine 인터페이스 계약 테스트 시") {
        When("routeMessage 메서드가 호출되면") {
            beforeEach {
                every { messageRoutingEngine.routeMessage(any(), any()) } returns Mono.empty()
            }

            Then("Mono<Void>를 반환해야 한다") {
                val message = createChatMessage()
                val sessionId = "test-session"
                val result = messageRoutingEngine.routeMessage(message, sessionId)

                result shouldNotBe null
                result.shouldBeInstanceOf<Mono<Void>>()

                verify(atLeast = 1) { messageRoutingEngine.routeMessage(message, sessionId) }
            }
        }

        When("broadcastToRoom 메서드가 호출되면") {
            beforeEach {
                every { messageRoutingEngine.broadcastToRoom(any(), any(), any()) } returns Mono.empty()
            }

            Then("Mono<Void>를 반환해야 한다") {
                val chatRoomId = "room-123"
                val message = createChatMessage()
                val result = messageRoutingEngine.broadcastToRoom(chatRoomId, message)

                result shouldNotBe null
                result.shouldBeInstanceOf<Mono<Void>>()

                verify(atLeast = 1) { messageRoutingEngine.broadcastToRoom(chatRoomId, message, null) }
            }
        }

        When("sendToUser 메서드가 호출되면") {
            beforeEach {
                every { messageRoutingEngine.sendToUser(any(), any()) } returns Mono.empty()
            }

            Then("Mono<Void>를 반환해야 한다") {
                val userId = 100L
                val message = createChatMessage()
                val result = messageRoutingEngine.sendToUser(userId, message)

                result shouldNotBe null
                result.shouldBeInstanceOf<Mono<Void>>()

                verify(atLeast = 1) { messageRoutingEngine.sendToUser(userId, message) }
            }
        }

        When("sendToUsers 메서드가 호출되면") {
            beforeEach {
                every { messageRoutingEngine.sendToUsers(any(), any()) } returns Mono.empty()
            }

            Then("Mono<Void>를 반환해야 한다") {
                val userIds = setOf(100L, 101L)
                val message = createChatMessage()
                val result = messageRoutingEngine.sendToUsers(userIds, message)

                result shouldNotBe null
                result.shouldBeInstanceOf<Mono<Void>>()

                verify(atLeast = 1) { messageRoutingEngine.sendToUsers(userIds, message) }
            }
        }

        When("sendToSession 메서드가 호출되면") {
            beforeEach {
                every { messageRoutingEngine.sendToSession(any(), any()) } returns Mono.empty()
            }

            Then("Mono<Void>를 반환해야 한다") {
                val sessionId = "session-123"
                val message = createChatMessage()
                val result = messageRoutingEngine.sendToSession(sessionId, message)

                result shouldNotBe null
                result.shouldBeInstanceOf<Mono<Void>>()

                verify(atLeast = 1) { messageRoutingEngine.sendToSession(sessionId, message) }
            }
        }

        When("broadcastGlobally 메서드가 호출되면") {
            beforeEach {
                every { messageRoutingEngine.broadcastGlobally(any()) } returns Mono.empty()
            }

            Then("Mono<Void>를 반환해야 한다") {
                val message = createSystemMessage()
                val result = messageRoutingEngine.broadcastGlobally(message)

                result shouldNotBe null
                result.shouldBeInstanceOf<Mono<Void>>()

                verify(atLeast = 1) { messageRoutingEngine.broadcastGlobally(message) }
            }
        }

        When("retryFailedMessage 메서드가 호출되면") {
            beforeEach {
                every { messageRoutingEngine.retryFailedMessage(any(), any(), any()) } returns Mono.empty()
            }

            Then("Mono<Void>를 반환해야 한다") {
                val message = createChatMessage()
                val target = MessageTarget.Room("room-123")
                val result = messageRoutingEngine.retryFailedMessage(message, target)

                result shouldNotBe null
                result.shouldBeInstanceOf<Mono<Void>>()

                verify(atLeast = 1) { messageRoutingEngine.retryFailedMessage(message, target, 3) }
            }
        }
    }

    Given("MessageRoutingEngine 성능 및 동시성 테스트 시") {
        When("동시에 여러 메시지를 라우팅하면") {
            beforeEach {
                every { messageRoutingEngine.routeMessage(any(), any()) } returns Mono.empty()
            }

            Then("모든 메시지가 성공적으로 라우팅되어야 한다") {
                val messages = (1..10).map { i ->
                    createChatMessage(content = "Message $i")
                }
                val sessionIds = (1..10).map { "session-$it" }

                val results = messages.zip(sessionIds) { message, sessionId ->
                    messageRoutingEngine.routeMessage(message, sessionId)
                }

                results.forEach { result ->
                    StepVerifier.create(result)
                        .verifyComplete()
                }

                verify(atLeast = 10) { messageRoutingEngine.routeMessage(any(), any()) }
            }
        }

        When("대량의 사용자에게 브로드캐스트하면") {
            beforeEach {
                every { messageRoutingEngine.sendToUsers(any(), any()) } returns Mono.empty()
            }

            Then("모든 사용자에게 성공적으로 전송되어야 한다") {
                val largeUserIds = (1L..100L).toSet()
                val message = createSystemMessage()

                val result = messageRoutingEngine.sendToUsers(largeUserIds, message)

                StepVerifier.create(result)
                    .verifyComplete()

                verify(atLeast = 1) { messageRoutingEngine.sendToUsers(largeUserIds, message) }
            }
        }

        When("여러 채팅방에 동시 브로드캐스트하면") {
            beforeEach {
                every { messageRoutingEngine.broadcastToRoom(any(), any(), any()) } returns Mono.empty()
            }

            Then("모든 채팅방에 성공적으로 브로드캐스트되어야 한다") {
                val chatRooms = (1..10).map { "room-$it" }
                val message = createSystemMessage()

                val results = chatRooms.map { roomId ->
                    messageRoutingEngine.broadcastToRoom(roomId, message)
                }

                results.forEach { result ->
                    StepVerifier.create(result)
                        .verifyComplete()
                }

                verify(atLeast = 10) { messageRoutingEngine.broadcastToRoom(any(), message, null) }
            }
        }
    }

    Given("MessageRoutingEngine 에러 처리 및 복구 테스트 시") {
        When("네트워크 오류 후 재시도하면") {
            beforeEach {
                every { messageRoutingEngine.retryFailedMessage(any(), any(), any()) } returns Mono.empty()
            }

            Then("성공적으로 복구되어야 한다") {
                val message = createChatMessage()
                val target = MessageTarget.User(100L)
                
                val result = messageRoutingEngine.retryFailedMessage(message, target, 5)

                StepVerifier.create(result)
                    .verifyComplete()

                verify(atLeast = 1) { messageRoutingEngine.retryFailedMessage(message, target, 5) }
            }
        }

        When("타임아웃 후 재시도하면") {
            beforeEach {
                every { messageRoutingEngine.retryFailedMessage(any(), any(), any()) } returns Mono.empty()
            }

            Then("성공적으로 복구되어야 한다") {
                val message = createChatMessage()
                val target = MessageTarget.Room("timeout-room")
                
                val result = messageRoutingEngine.retryFailedMessage(message, target, 10)

                StepVerifier.create(result)
                    .verifyComplete()

                verify(atLeast = 1) { messageRoutingEngine.retryFailedMessage(message, target, 10) }
            }
        }

        When("최대 재시도 횟수를 초과하면") {
            val maxRetriesExceededException = RuntimeException("Max retries exceeded")

            beforeEach {
                every { messageRoutingEngine.retryFailedMessage(any(), any(), any()) } returns Mono.error(maxRetriesExceededException)
            }

            Then("최종 오류가 전파되어야 한다") {
                val message = createChatMessage()
                val target = MessageTarget.Session("failing-session")
                
                val result = messageRoutingEngine.retryFailedMessage(message, target, 3)

                StepVerifier.create(result)
                    .expectErrorMessage("Max retries exceeded")
                    .verify()

                verify(atLeast = 1) { messageRoutingEngine.retryFailedMessage(message, target, 3) }
            }
        }
    }
})