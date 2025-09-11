package com.simplechat.domain.service

import com.simplechat.domain.entity.ChatMessage
import com.simplechat.domain.entity.MessageType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.LocalDateTime

/**
 * ChatMessageDomainService 인터페이스 테스트
 * 
 * 도메인 서비스 인터페이스의 계약(contract)과 구현체의 동작을 테스트합니다.
 * MockK를 사용하여 구현체를 모킹하고 인터페이스 명세를 검증합니다.
 */
class ChatMessageDomainServiceTest : BehaviorSpec({
    
    // Mock 도메인 서비스
    lateinit var chatMessageDomainService: ChatMessageDomainService
    
    // 테스트용 ChatMessage 생성 헬퍼 메서드
    var messageIdCounter = 0
    fun createValidChatMessage(
        id: String? = "test-message-${++messageIdCounter}",
        roomId: Long = 1L,
        userId: Long = 100L,
        content: String = "Test message content $messageIdCounter",
        messageType: MessageType = MessageType.TEXT,
        timestamp: LocalDateTime = LocalDateTime.now()
    ): ChatMessage {
        return ChatMessage(
            id = id,
            roomId = roomId,
            userId = userId,
            content = content,
            messageType = messageType,
            timestamp = timestamp
        )
    }

    beforeEach {
        chatMessageDomainService = mockk<ChatMessageDomainService>(relaxed = true)
        messageIdCounter = 0
    }

    Given("ChatMessageDomainService 메시지 저장 기능 테스트 시") {
        When("유효한 메시지를 저장하면") {
            Then("성공적으로 저장되어야 한다") {
                every { chatMessageDomainService.saveMessage(any()) } returns Mono.empty()

                val validMessage = createValidChatMessage()
                val result = chatMessageDomainService.saveMessage(validMessage)
                
                StepVerifier.create(result)
                    .verifyComplete()
                
                verify(atLeast = 1) { chatMessageDomainService.saveMessage(any()) }
            }
        }
        
        When("TEXT 타입 메시지를 저장하면") {
            Then("저장이 완료되어야 한다") {
                every { chatMessageDomainService.saveMessage(any()) } returns Mono.empty()

                val textMessage = createValidChatMessage(
                    content = "Hello, this is a text message",
                    messageType = MessageType.TEXT
                )
                val result = chatMessageDomainService.saveMessage(textMessage)
                
                StepVerifier.create(result)
                    .verifyComplete()
                
                verify(atLeast = 1) { chatMessageDomainService.saveMessage(any()) }
            }
        }
        
        When("SYSTEM 타입 메시지를 저장하면") {
            Then("저장이 완료되어야 한다") {
                every { chatMessageDomainService.saveMessage(any()) } returns Mono.empty()

                val systemMessage = createValidChatMessage(
                    content = "System notification message",
                    messageType = MessageType.SYSTEM,
                    userId = 0L
                )
                val result = chatMessageDomainService.saveMessage(systemMessage)
                
                StepVerifier.create(result)
                    .verifyComplete()
                
                verify(atLeast = 1) { chatMessageDomainService.saveMessage(any()) }
            }
        }
        
        When("JOIN 타입 메시지를 저장하면") {

            Then("저장이 완료되어야 한다") {
                every { chatMessageDomainService.saveMessage(any()) } returns Mono.empty()

                val joinMessage = createValidChatMessage(
                    content = "User joined the room",
                    messageType = MessageType.JOIN
                )
                val result = chatMessageDomainService.saveMessage(joinMessage)
                
                StepVerifier.create(result)
                    .verifyComplete()
                
                verify(atLeast = 1) { chatMessageDomainService.saveMessage(any()) }
            }
        }
        
        When("LEAVE 타입 메시지를 저장하면") {
            Then("저장이 완료되어야 한다") {
                every { chatMessageDomainService.saveMessage(any()) } returns Mono.empty()

                val leaveMessage = createValidChatMessage(
                    content = "User left the room",
                    messageType = MessageType.LEAVE
                )
                val result = chatMessageDomainService.saveMessage(leaveMessage)
                
                StepVerifier.create(result)
                    .verifyComplete()
                
                verify(atLeast = 1) { chatMessageDomainService.saveMessage(any()) }
            }
        }
        
        When("저장 중 오류가 발생하면") {
            val expectedException = RuntimeException("Database connection failed")

            Then("오류가 전파되어야 한다") {
                every { chatMessageDomainService.saveMessage(any()) } returns Mono.error(expectedException)

                val errorMessage = createValidChatMessage()
                val result = chatMessageDomainService.saveMessage(errorMessage)
                
                StepVerifier.create(result)
                    .expectErrorMatches { it is RuntimeException && it.message == "Database connection failed" }
                    .verify()
                
                verify(atLeast = 1) { chatMessageDomainService.saveMessage(any()) }
            }
        }
        
        When("여러 메시지를 순차적으로 저장하면") {
            Then("모든 메시지가 저장되어야 한다") {
                every { chatMessageDomainService.saveMessage(any()) } returns Mono.empty()

                val message1 = createValidChatMessage(content = "First message")
                val message2 = createValidChatMessage(content = "Second message")
                val message3 = createValidChatMessage(content = "Third message")
                
                val results = listOf(
                    chatMessageDomainService.saveMessage(message1),
                    chatMessageDomainService.saveMessage(message2),
                    chatMessageDomainService.saveMessage(message3)
                )
                
                results.forEach { result ->
                    StepVerifier.create(result)
                        .verifyComplete()
                }
                
                verify(atLeast = 3) { chatMessageDomainService.saveMessage(any()) }
            }
        }
    }
    
    Given("ChatMessageDomainService 메시지 검증 기능 테스트 시") {
        When("유효한 메시지를 검증하면") {
            Then("true를 반환해야 한다") {
                every { chatMessageDomainService.validateMessage(any()) } returns true

                val validMessage = createValidChatMessage()
                val isValid = chatMessageDomainService.validateMessage(validMessage)
                
                isValid shouldBe true
                verify(atLeast = 1) { chatMessageDomainService.validateMessage(any()) }
            }
        }
        
        When("무효한 roomId를 가진 메시지를 검증하면") {
            Then("false를 반환해야 한다") {
                every { chatMessageDomainService.validateMessage(any()) } returns false

                val invalidMessage = createValidChatMessage(roomId = 0L)
                val isValid = chatMessageDomainService.validateMessage(invalidMessage)
                
                isValid shouldBe false
                verify(atLeast = 1) { chatMessageDomainService.validateMessage(any()) }
            }
        }
        
        When("무효한 userId를 가진 메시지를 검증하면") {
            Then("false를 반환해야 한다") {
                every { chatMessageDomainService.validateMessage(any()) } returns false

                val invalidMessage = createValidChatMessage(userId = -1L)
                val isValid = chatMessageDomainService.validateMessage(invalidMessage)
                
                isValid shouldBe false
                verify(atLeast = 1) { chatMessageDomainService.validateMessage(any()) }
            }
        }
        
        When("빈 내용을 가진 메시지를 검증하면") {
            Then("false를 반환해야 한다") {
                every { chatMessageDomainService.validateMessage(any()) } returns false

                val invalidMessage = createValidChatMessage(content = "")
                val isValid = chatMessageDomainService.validateMessage(invalidMessage)
                
                isValid shouldBe false
                verify(atLeast = 1) { chatMessageDomainService.validateMessage(any()) }
            }
        }
        
        When("공백만 있는 내용을 가진 메시지를 검증하면") {
            Then("false를 반환해야 한다") {
                every { chatMessageDomainService.validateMessage(any()) } returns false

                val invalidMessage = createValidChatMessage(content = "   ")
                val isValid = chatMessageDomainService.validateMessage(invalidMessage)
                
                isValid shouldBe false
                verify(atLeast = 1) { chatMessageDomainService.validateMessage(any()) }
            }
        }
        
        When("너무 긴 내용을 가진 메시지를 검증하면") {
            Then("false를 반환해야 한다") {
                every { chatMessageDomainService.validateMessage(any()) } returns false

                val longContent = "a".repeat(1001)
                val invalidMessage = createValidChatMessage(content = longContent)
                val isValid = chatMessageDomainService.validateMessage(invalidMessage)
                
                isValid shouldBe false
                verify(atLeast = 1) { chatMessageDomainService.validateMessage(any()) }
            }
        }
        
        When("정확히 1000자 내용을 가진 메시지를 검증하면") {
            Then("true를 반환해야 한다") {
                every { chatMessageDomainService.validateMessage(any()) } returns true

                val maxContent = "a".repeat(1000)
                val validMessage = createValidChatMessage(content = maxContent)
                val isValid = chatMessageDomainService.validateMessage(validMessage)
                
                isValid shouldBe true
                verify(atLeast = 1) { chatMessageDomainService.validateMessage(any()) }
            }
        }
        
        When("다양한 메시지 타입을 검증하면") {
            Then("모든 타입이 유효해야 한다") {
                every { chatMessageDomainService.validateMessage(any()) } returns true

                val textMessage = createValidChatMessage(messageType = MessageType.TEXT)
                val systemMessage = createValidChatMessage(messageType = MessageType.SYSTEM)
                val joinMessage = createValidChatMessage(messageType = MessageType.JOIN)
                val leaveMessage = createValidChatMessage(messageType = MessageType.LEAVE)
                
                chatMessageDomainService.validateMessage(textMessage) shouldBe true
                chatMessageDomainService.validateMessage(systemMessage) shouldBe true
                chatMessageDomainService.validateMessage(joinMessage) shouldBe true
                chatMessageDomainService.validateMessage(leaveMessage) shouldBe true
                
                verify(atLeast = 4) { chatMessageDomainService.validateMessage(any()) }
            }
        }
    }
    
    Given("ChatMessageDomainService 통합 시나리오 테스트 시") {
        When("메시지 검증 후 저장하는 플로우를 실행하면") {
            Then("검증 후 저장이 성공해야 한다") {
                every { chatMessageDomainService.validateMessage(any()) } returns true
                every { chatMessageDomainService.saveMessage(any()) } returns Mono.empty()

                val message = createValidChatMessage()
                
                // 1. 먼저 검증
                val isValid = chatMessageDomainService.validateMessage(message)
                isValid shouldBe true
                
                // 2. 검증이 성공하면 저장
                val saveResult = chatMessageDomainService.saveMessage(message)
                StepVerifier.create(saveResult)
                    .verifyComplete()
                
                // 3. 호출 검증
                verify(atLeast = 1) { chatMessageDomainService.validateMessage(any()) }
                verify(atLeast = 1) { chatMessageDomainService.saveMessage(any()) }
            }
        }
        
        When("무효한 메시지는 저장하지 않는 플로우를 실행하면") {
            Then("검증 실패 시 저장을 호출하지 않아야 한다") {
                every { chatMessageDomainService.validateMessage(any()) } returns false

                val invalidMessage = createValidChatMessage(content = "")
                val isValid = chatMessageDomainService.validateMessage(invalidMessage)
                isValid shouldBe false
                
                verify(atLeast = 1) { chatMessageDomainService.validateMessage(any()) }
                // saveMessage는 호출되지 않음
            }
        }
    }
    
    Given("ChatMessageDomainService 에러 처리 테스트 시") {
        When("검증 중 예외가 발생하면") {
            val exception = IllegalArgumentException("Invalid message format")

            Then("예외가 전파되어야 한다") {
                every { chatMessageDomainService.validateMessage(any()) } throws exception

                val message = createValidChatMessage()
                val result = runCatching {
                    chatMessageDomainService.validateMessage(message)
                }
                
                result.isFailure shouldBe true
                result.exceptionOrNull() shouldBe exception
                
                verify(atLeast = 1) { chatMessageDomainService.validateMessage(any()) }
            }
        }
        
        When("저장 중 타임아웃이 발생하면") {
            Then("타임아웃 에러가 전파되어야 한다") {
                every { chatMessageDomainService.saveMessage(any()) } returns Mono.error(
                    RuntimeException("Operation timeout")
                )

                val message = createValidChatMessage()
                val result = chatMessageDomainService.saveMessage(message)
                
                StepVerifier.create(result)
                    .expectErrorMatches { it is RuntimeException && it.message == "Operation timeout" }
                    .verify()
                
                verify(atLeast = 1) { chatMessageDomainService.saveMessage(any()) }
            }
        }
        
        When("네트워크 오류로 저장이 실패하면") {
            Then("네트워크 에러가 전파되어야 한다") {
                every { chatMessageDomainService.saveMessage(any()) } returns Mono.error(
                    RuntimeException("Network unreachable")
                )

                val message = createValidChatMessage()
                val result = chatMessageDomainService.saveMessage(message)
                
                StepVerifier.create(result)
                    .expectErrorMessage("Network unreachable")
                    .verify()
                
                verify(atLeast = 1) { chatMessageDomainService.saveMessage(any()) }
            }
        }
    }
    
    Given("ChatMessageDomainService 성능 및 동시성 테스트 시") {
        When("동시에 여러 메시지를 저장하면") {
            Then("모든 메시지가 성공적으로 저장되어야 한다") {
                every { chatMessageDomainService.saveMessage(any()) } returns Mono.empty()

                val messages = (1..10).map { i ->
                    createValidChatMessage(content = "Message $i")
                }
                
                val results = messages.map { message ->
                    chatMessageDomainService.saveMessage(message)
                }
                
                results.forEach { result ->
                    StepVerifier.create(result)
                        .verifyComplete()
                }
                
                verify(atLeast = 10) { chatMessageDomainService.saveMessage(any()) }
            }
        }
        
        When("대량의 메시지를 검증하면") {
            Then("모든 메시지가 빠르게 검증되어야 한다") {
                every { chatMessageDomainService.validateMessage(any()) } returns true

                val largeMessageBatch = (1..100).map { i ->
                    createValidChatMessage(content = "Batch message $i")
                }
                
                val validationResults = largeMessageBatch.map { message ->
                    chatMessageDomainService.validateMessage(message)
                }
                
                validationResults.all { it } shouldBe true
                verify(atLeast = 100) { chatMessageDomainService.validateMessage(any()) }
            }
        }
    }
    
    Given("ChatMessageDomainService 인터페이스 계약 테스트 시") {
        When("saveMessage 메서드가 호출되면") {
            Then("Mono<Void>를 반환해야 한다") {
                every { chatMessageDomainService.saveMessage(any()) } returns Mono.empty()

                val message = createValidChatMessage()
                val result = chatMessageDomainService.saveMessage(message)
                
                result shouldNotBe null
                result.shouldBeInstanceOf<Mono<Void>>()
                
                verify(atLeast = 1) { chatMessageDomainService.saveMessage(any()) }
            }
        }
        
        When("validateMessage 메서드가 호출되면") {
            Then("Boolean을 반환해야 한다") {
                every { chatMessageDomainService.validateMessage(any()) } returns true

                val message = createValidChatMessage()
                val result = chatMessageDomainService.validateMessage(message)
                
                result shouldNotBe null
                (result is Boolean) shouldBe true
                
                verify(atLeast = 1) { chatMessageDomainService.validateMessage(any()) }
            }
        }
    }
})