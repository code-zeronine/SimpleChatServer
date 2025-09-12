package com.simplechat.domain.service

import com.simplechat.domain.message.websocket.*
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * MessageBrokerDomainService 인터페이스 테스트
 *
 * 메시지 브로커 도메인 서비스의 계약(contract)과 구현체의 동작을 테스트합니다.
 * MockK를 사용하여 구현체를 모킹하고 인터페이스 명세를 검증합니다.
 */
class MessageBrokerDomainServiceTest : BehaviorSpec({

    // Mock 메시지 브로커 도메인 서비스
    lateinit var messageBrokerDomainService: MessageBrokerDomainService

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

    beforeEach {
        messageBrokerDomainService = mockk<MessageBrokerDomainService>(relaxed = true)
        messageIdCounter = 0
    }

    Given("MessageBrokerDomainService 브로드캐스트 기능 테스트 시") {
        When("채팅방에 채팅 메시지를 브로드캐스트하면") {
            beforeEach {
                every { messageBrokerDomainService.broadcast(any(), any()) } just Runs
            }

            Then("성공적으로 브로드캐스트되어야 한다") {
                val chatRoomId = "room-123"
                val chatMessage = createChatMessage(roomId = 123L, content = "Hello everyone!")
                
                messageBrokerDomainService.broadcast(chatRoomId, chatMessage)

                verify(exactly = 1) { messageBrokerDomainService.broadcast(chatRoomId, chatMessage) }
            }
        }

        When("채팅방에 JOIN 메시지를 브로드캐스트하면") {
            beforeEach {
                every { messageBrokerDomainService.broadcast(any(), any()) } just Runs
            }

            Then("성공적으로 브로드캐스트되어야 한다") {
                val chatRoomId = "room-456"
                val joinMessage = createJoinMessage(roomId = 456L, userNickname = "NewUser")
                
                messageBrokerDomainService.broadcast(chatRoomId, joinMessage)

                verify(exactly = 1) { messageBrokerDomainService.broadcast(chatRoomId, joinMessage) }
            }
        }

        When("채팅방에 LEAVE 메시지를 브로드캐스트하면") {
            beforeEach {
                every { messageBrokerDomainService.broadcast(any(), any()) } just Runs
            }

            Then("성공적으로 브로드캐스트되어야 한다") {
                val chatRoomId = "room-789"
                val leaveMessage = createLeaveMessage(roomId = 789L, userNickname = "LeavingUser")
                
                messageBrokerDomainService.broadcast(chatRoomId, leaveMessage)

                verify(exactly = 1) { messageBrokerDomainService.broadcast(chatRoomId, leaveMessage) }
            }
        }

        When("채팅방에 TYPING 메시지를 브로드캐스트하면") {
            beforeEach {
                every { messageBrokerDomainService.broadcast(any(), any()) } just Runs
            }

            Then("성공적으로 브로드캐스트되어야 한다") {
                val chatRoomId = "room-101"
                val typingMessage = createTypingMessage(roomId = 101L, isTyping = true)
                
                messageBrokerDomainService.broadcast(chatRoomId, typingMessage)

                verify(exactly = 1) { messageBrokerDomainService.broadcast(chatRoomId, typingMessage) }
            }
        }

        When("채팅방에 SYSTEM 메시지를 브로드캐스트하면") {
            beforeEach {
                every { messageBrokerDomainService.broadcast(any(), any()) } just Runs
            }

            Then("성공적으로 브로드캐스트되어야 한다") {
                val chatRoomId = "room-202"
                val systemMessage = createSystemMessage(content = "Server maintenance in 5 minutes")
                
                messageBrokerDomainService.broadcast(chatRoomId, systemMessage)

                verify(exactly = 1) { messageBrokerDomainService.broadcast(chatRoomId, systemMessage) }
            }
        }

        When("여러 채팅방에 동시에 브로드캐스트하면") {
            beforeEach {
                every { messageBrokerDomainService.broadcast(any(), any()) } just Runs
            }

            Then("모든 채팅방에 성공적으로 브로드캐스트되어야 한다") {
                val chatRooms = listOf("room-1", "room-2", "room-3")
                val message = createChatMessage(content = "Broadcast to all rooms")
                
                chatRooms.forEach { roomId ->
                    messageBrokerDomainService.broadcast(roomId, message)
                }

                verify(exactly = 3) { messageBrokerDomainService.broadcast(any(), message) }
                chatRooms.forEach { roomId ->
                    verify(exactly = 1) { messageBrokerDomainService.broadcast(roomId, message) }
                }
            }
        }

        When("빈 채팅방 ID로 브로드캐스트하면") {
            beforeEach {
                every { messageBrokerDomainService.broadcast(any(), any()) } just Runs
            }

            Then("빈 채팅방 ID로도 호출되어야 한다") {
                val emptyChatRoomId = ""
                val message = createChatMessage()
                
                messageBrokerDomainService.broadcast(emptyChatRoomId, message)

                verify(exactly = 1) { messageBrokerDomainService.broadcast(emptyChatRoomId, message) }
            }
        }

        When("특수한 채팅방 ID로 브로드캐스트하면") {
            beforeEach {
                every { messageBrokerDomainService.broadcast(any(), any()) } just Runs
            }

            Then("특수한 채팅방 ID로도 호출되어야 한다") {
                val specialChatRoomIds = listOf("room-123", "room_with_underscore", "room.with.dots", "room-한글")
                val message = createChatMessage()
                
                specialChatRoomIds.forEach { chatRoomId ->
                    messageBrokerDomainService.broadcast(chatRoomId, message)
                }

                specialChatRoomIds.forEach { chatRoomId ->
                    verify(exactly = 1) { messageBrokerDomainService.broadcast(chatRoomId, message) }
                }
            }
        }
    }

    Given("MessageBrokerDomainService 사용자별 메시지 전송 기능 테스트 시") {
        When("특정 사용자에게 채팅 메시지를 전송하면") {
            beforeEach {
                every { messageBrokerDomainService.sendToUser(any(), any()) } just Runs
            }

            Then("성공적으로 전송되어야 한다") {
                val userId = 100L
                val chatMessage = createChatMessage(userId = userId, content = "Private message")
                
                messageBrokerDomainService.sendToUser(userId, chatMessage)

                verify(exactly = 1) { messageBrokerDomainService.sendToUser(userId, chatMessage) }
            }
        }

        When("특정 사용자에게 시스템 메시지를 전송하면") {
            beforeEach {
                every { messageBrokerDomainService.sendToUser(any(), any()) } just Runs
            }

            Then("성공적으로 전송되어야 한다") {
                val userId = 200L
                val systemMessage = createSystemMessage(content = "Welcome to the chat!")
                
                messageBrokerDomainService.sendToUser(userId, systemMessage)

                verify(exactly = 1) { messageBrokerDomainService.sendToUser(userId, systemMessage) }
            }
        }

        When("여러 사용자에게 개별적으로 메시지를 전송하면") {
            beforeEach {
                every { messageBrokerDomainService.sendToUser(any(), any()) } just Runs
            }

            Then("모든 사용자에게 성공적으로 전송되어야 한다") {
                val userIds = listOf(100L, 200L, 300L)
                val message = createChatMessage(content = "Individual message")
                
                userIds.forEach { userId ->
                    messageBrokerDomainService.sendToUser(userId, message)
                }

                verify(exactly = 3) { messageBrokerDomainService.sendToUser(any(), message) }
                userIds.forEach { userId ->
                    verify(exactly = 1) { messageBrokerDomainService.sendToUser(userId, message) }
                }
            }
        }

        When("동일한 사용자에게 여러 메시지를 전송하면") {
            beforeEach {
                every { messageBrokerDomainService.sendToUser(any(), any()) } just Runs
            }

            Then("모든 메시지가 성공적으로 전송되어야 한다") {
                val userId = 100L
                val messages = listOf(
                    createChatMessage(content = "Message 1"),
                    createChatMessage(content = "Message 2"),
                    createSystemMessage(content = "System alert")
                )
                
                messages.forEach { message ->
                    messageBrokerDomainService.sendToUser(userId, message)
                }

                verify(exactly = 3) { messageBrokerDomainService.sendToUser(userId, any()) }
                messages.forEach { message ->
                    verify(exactly = 1) { messageBrokerDomainService.sendToUser(userId, message) }
                }
            }
        }

        When("0 또는 음수 사용자 ID로 메시지를 전송하면") {
            beforeEach {
                every { messageBrokerDomainService.sendToUser(any(), any()) } just Runs
            }

            Then("해당 사용자 ID로 호출되어야 한다") {
                val invalidUserIds = listOf(0L, -1L, -100L)
                val message = createChatMessage()
                
                invalidUserIds.forEach { userId ->
                    messageBrokerDomainService.sendToUser(userId, message)
                }

                invalidUserIds.forEach { userId ->
                    verify(exactly = 1) { messageBrokerDomainService.sendToUser(userId, message) }
                }
            }
        }

        When("매우 큰 사용자 ID로 메시지를 전송하면") {
            beforeEach {
                every { messageBrokerDomainService.sendToUser(any(), any()) } just Runs
            }

            Then("해당 사용자 ID로 호출되어야 한다") {
                val largeUserId = Long.MAX_VALUE
                val message = createChatMessage()
                
                messageBrokerDomainService.sendToUser(largeUserId, message)

                verify(exactly = 1) { messageBrokerDomainService.sendToUser(largeUserId, message) }
            }
        }

        When("다양한 메시지 타입을 사용자에게 전송하면") {
            beforeEach {
                every { messageBrokerDomainService.sendToUser(any(), any()) } just Runs
            }

            Then("모든 메시지 타입이 성공적으로 전송되어야 한다") {
                val userId = 100L
                val messages = listOf(
                    createChatMessage(content = "Chat message"),
                    createJoinMessage(userNickname = "JoinUser"),
                    createLeaveMessage(userNickname = "LeaveUser"),
                    createTypingMessage(isTyping = true),
                    createSystemMessage(content = "System message")
                )
                
                messages.forEach { message ->
                    messageBrokerDomainService.sendToUser(userId, message)
                }

                messages.forEach { message ->
                    verify(exactly = 1) { messageBrokerDomainService.sendToUser(userId, message) }
                }
            }
        }
    }

    Given("MessageBrokerDomainService 복합 시나리오 테스트 시") {
        When("브로드캐스트와 개별 전송을 동시에 수행하면") {
            beforeEach {
                every { messageBrokerDomainService.broadcast(any(), any()) } just Runs
                every { messageBrokerDomainService.sendToUser(any(), any()) } just Runs
            }

            Then("모든 작업이 성공적으로 수행되어야 한다") {
                val chatRoomId = "room-mixed"
                val userId = 100L
                val broadcastMessage = createChatMessage(content = "Broadcast message")
                val privateMessage = createChatMessage(content = "Private message")
                
                messageBrokerDomainService.broadcast(chatRoomId, broadcastMessage)
                messageBrokerDomainService.sendToUser(userId, privateMessage)

                verify(exactly = 1) { messageBrokerDomainService.broadcast(chatRoomId, broadcastMessage) }
                verify(exactly = 1) { messageBrokerDomainService.sendToUser(userId, privateMessage) }
            }
        }

        When("동일한 메시지를 브로드캐스트와 개별 전송으로 모두 보내면") {
            beforeEach {
                every { messageBrokerDomainService.broadcast(any(), any()) } just Runs
                every { messageBrokerDomainService.sendToUser(any(), any()) } just Runs
            }

            Then("두 방식 모두 성공적으로 수행되어야 한다") {
                val chatRoomId = "room-duplicate"
                val userId = 200L
                val message = createChatMessage(content = "Shared message")
                
                messageBrokerDomainService.broadcast(chatRoomId, message)
                messageBrokerDomainService.sendToUser(userId, message)

                verify(exactly = 1) { messageBrokerDomainService.broadcast(chatRoomId, message) }
                verify(exactly = 1) { messageBrokerDomainService.sendToUser(userId, message) }
            }
        }

        When("채팅방 이벤트 시퀀스를 처리하면") {
            beforeEach {
                every { messageBrokerDomainService.broadcast(any(), any()) } just Runs
                every { messageBrokerDomainService.sendToUser(any(), any()) } just Runs
            }

            Then("모든 이벤트가 순서대로 처리되어야 한다") {
                val chatRoomId = "room-sequence"
                val userId = 300L
                
                // 1. 사용자 입장
                val joinMessage = createJoinMessage(roomId = 300L, userId = userId)
                messageBrokerDomainService.broadcast(chatRoomId, joinMessage)
                
                // 2. 환영 메시지 개별 전송
                val welcomeMessage = createSystemMessage(content = "Welcome to the room!")
                messageBrokerDomainService.sendToUser(userId, welcomeMessage)
                
                // 3. 채팅 메시지 브로드캐스트
                val chatMessage = createChatMessage(content = "Hello everyone!", userId = userId)
                messageBrokerDomainService.broadcast(chatRoomId, chatMessage)
                
                // 4. 타이핑 상태 브로드캐스트
                val typingMessage = createTypingMessage(roomId = 300L, userId = userId, isTyping = true)
                messageBrokerDomainService.broadcast(chatRoomId, typingMessage)
                
                // 5. 사용자 퇴장
                val leaveMessage = createLeaveMessage(roomId = 300L, userId = userId)
                messageBrokerDomainService.broadcast(chatRoomId, leaveMessage)

                // 검증
                verify(exactly = 4) { messageBrokerDomainService.broadcast(chatRoomId, any()) }
                verify(exactly = 1) { messageBrokerDomainService.sendToUser(userId, welcomeMessage) }
                
                verify(exactly = 1) { messageBrokerDomainService.broadcast(chatRoomId, joinMessage) }
                verify(exactly = 1) { messageBrokerDomainService.broadcast(chatRoomId, chatMessage) }
                verify(exactly = 1) { messageBrokerDomainService.broadcast(chatRoomId, typingMessage) }
                verify(exactly = 1) { messageBrokerDomainService.broadcast(chatRoomId, leaveMessage) }
            }
        }
    }

    Given("MessageBrokerDomainService 성능 및 동시성 테스트 시") {
        When("대량의 메시지를 브로드캐스트하면") {
            beforeEach {
                every { messageBrokerDomainService.broadcast(any(), any()) } just Runs
            }

            Then("모든 메시지가 성공적으로 브로드캐스트되어야 한다") {
                val chatRoomId = "room-bulk"
                val messages = (1..100).map { i ->
                    createChatMessage(content = "Bulk message $i")
                }
                
                messages.forEach { message ->
                    messageBrokerDomainService.broadcast(chatRoomId, message)
                }

                verify(exactly = 100) { messageBrokerDomainService.broadcast(chatRoomId, any()) }
                messages.forEach { message ->
                    verify(exactly = 1) { messageBrokerDomainService.broadcast(chatRoomId, message) }
                }
            }
        }

        When("대량의 사용자에게 개별 메시지를 전송하면") {
            beforeEach {
                every { messageBrokerDomainService.sendToUser(any(), any()) } just Runs
            }

            Then("모든 사용자에게 성공적으로 전송되어야 한다") {
                val userIds = (1L..100L).toList()
                val message = createSystemMessage(content = "Mass notification")
                
                userIds.forEach { userId ->
                    messageBrokerDomainService.sendToUser(userId, message)
                }

                verify(exactly = 100) { messageBrokerDomainService.sendToUser(any(), message) }
                userIds.forEach { userId ->
                    verify(exactly = 1) { messageBrokerDomainService.sendToUser(userId, message) }
                }
            }
        }

        When("다양한 메시지 타입을 혼합해서 처리하면") {
            beforeEach {
                every { messageBrokerDomainService.broadcast(any(), any()) } just Runs
                every { messageBrokerDomainService.sendToUser(any(), any()) } just Runs
            }

            Then("모든 메시지 타입이 올바르게 처리되어야 한다") {
                val chatRoomId = "room-mixed-types"
                val userId = 500L
                
                val chatMessage = createChatMessage()
                val joinMessage = createJoinMessage()
                val leaveMessage = createLeaveMessage()
                val typingMessage = createTypingMessage()
                val systemMessage = createSystemMessage()
                
                // 브로드캐스트 메시지들
                messageBrokerDomainService.broadcast(chatRoomId, chatMessage)
                messageBrokerDomainService.broadcast(chatRoomId, joinMessage)
                messageBrokerDomainService.broadcast(chatRoomId, leaveMessage)
                messageBrokerDomainService.broadcast(chatRoomId, typingMessage)
                
                // 개별 전송 메시지들
                messageBrokerDomainService.sendToUser(userId, chatMessage)
                messageBrokerDomainService.sendToUser(userId, systemMessage)

                verify(exactly = 4) { messageBrokerDomainService.broadcast(chatRoomId, any()) }
                verify(exactly = 2) { messageBrokerDomainService.sendToUser(userId, any()) }
            }
        }
    }

    Given("MessageBrokerDomainService 인터페이스 계약 테스트 시") {
        When("broadcast 메서드가 호출되면") {
            beforeEach {
                every { messageBrokerDomainService.broadcast(any(), any()) } just Runs
            }

            Then("반환값 없이 성공적으로 실행되어야 한다") {
                val chatRoomId = "room-contract"
                val message = createChatMessage()
                
                val result = messageBrokerDomainService.broadcast(chatRoomId, message)
                
                result shouldBe Unit
                verify(exactly = 1) { messageBrokerDomainService.broadcast(chatRoomId, message) }
            }
        }

        When("sendToUser 메서드가 호출되면") {
            beforeEach {
                every { messageBrokerDomainService.sendToUser(any(), any()) } just Runs
            }

            Then("반환값 없이 성공적으로 실행되어야 한다") {
                val userId = 600L
                val message = createChatMessage()
                
                val result = messageBrokerDomainService.sendToUser(userId, message)
                
                result shouldBe Unit
                verify(exactly = 1) { messageBrokerDomainService.sendToUser(userId, message) }
            }
        }

        When("메서드들이 연속적으로 호출되면") {
            beforeEach {
                every { messageBrokerDomainService.broadcast(any(), any()) } just Runs
                every { messageBrokerDomainService.sendToUser(any(), any()) } just Runs
            }

            Then("모든 호출이 독립적으로 처리되어야 한다") {
                val chatRoomId = "room-sequential"
                val userId = 700L
                val message1 = createChatMessage(content = "First message")
                val message2 = createChatMessage(content = "Second message")
                
                messageBrokerDomainService.broadcast(chatRoomId, message1)
                messageBrokerDomainService.sendToUser(userId, message2)
                messageBrokerDomainService.broadcast(chatRoomId, message2)
                messageBrokerDomainService.sendToUser(userId, message1)

                verify(exactly = 2) { messageBrokerDomainService.broadcast(chatRoomId, any()) }
                verify(exactly = 2) { messageBrokerDomainService.sendToUser(userId, any()) }
                
                verify(exactly = 1) { messageBrokerDomainService.broadcast(chatRoomId, message1) }
                verify(exactly = 1) { messageBrokerDomainService.broadcast(chatRoomId, message2) }
                verify(exactly = 1) { messageBrokerDomainService.sendToUser(userId, message1) }
                verify(exactly = 1) { messageBrokerDomainService.sendToUser(userId, message2) }
            }
        }
    }

    Given("MessageBrokerDomainService 메서드 시그니처 검증 테스트 시") {
        When("broadcast 메서드의 파라미터를 검증하면") {
            beforeEach {
                every { messageBrokerDomainService.broadcast(any(), any()) } just Runs
            }

            Then("올바른 타입의 파라미터를 받아야 한다") {
                val chatRoomId: String = "room-type-check"
                val message: WebSocketMessage = createChatMessage()
                
                messageBrokerDomainService.broadcast(chatRoomId, message)

                // 타입 체크를 위한 컴파일 타임 검증
                chatRoomId shouldNotBe null
                message shouldNotBe null
                verify(exactly = 1) { messageBrokerDomainService.broadcast(chatRoomId, message) }
            }
        }

        When("sendToUser 메서드의 파라미터를 검증하면") {
            beforeEach {
                every { messageBrokerDomainService.sendToUser(any(), any()) } just Runs
            }

            Then("올바른 타입의 파라미터를 받아야 한다") {
                val userId: Long = 800L
                val message: WebSocketMessage = createSystemMessage()
                
                messageBrokerDomainService.sendToUser(userId, message)

                // 타입 체크를 위한 컴파일 타임 검증
                userId shouldNotBe null
                message shouldNotBe null
                verify(exactly = 1) { messageBrokerDomainService.sendToUser(userId, message) }
            }
        }
    }
})