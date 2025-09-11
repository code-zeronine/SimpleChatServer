package com.simplechat.domain.entity

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import java.time.LocalDateTime

/**
 * ChatMessage 엔티티 도메인 로직 테스트
 * 
 * 순수한 도메인 객체의 비즈니스 로직과 검증 규칙을 테스트합니다.
 */
class ChatMessageTest : BehaviorSpec({
    
    Given("ChatMessage 생성 시") {
        val validRoomId = 1L
        val validUserId = 100L
        val validContent = "Hello, World!"
        val currentTime = LocalDateTime.now()
        
        When("유효한 데이터로 생성하면") {
            val chatMessage = ChatMessage(
                id = "test-id-123",
                roomId = validRoomId,
                userId = validUserId,
                content = validContent,
                timestamp = currentTime,
                messageType = MessageType.TEXT
            )
            
            Then("모든 속성이 올바르게 설정되어야 한다") {
                chatMessage.id shouldBe "test-id-123"
                chatMessage.roomId shouldBe validRoomId
                chatMessage.userId shouldBe validUserId
                chatMessage.content shouldBe validContent
                chatMessage.timestamp shouldBe currentTime
                chatMessage.messageType shouldBe MessageType.TEXT
            }
        }
        
        When("기본값으로 생성하면") {
            val chatMessage = ChatMessage(
                roomId = validRoomId,
                userId = validUserId,
                content = validContent
            )
            
            Then("기본값들이 올바르게 설정되어야 한다") {
                chatMessage.id shouldBe null
                chatMessage.messageType shouldBe MessageType.TEXT
                chatMessage.timestamp shouldNotBe null
            }
        }
    }
    
    Given("ChatMessage 유효성 검증 시") {
        When("모든 필드가 유효하면") {
            val validMessage = ChatMessage(
                roomId = 1L,
                userId = 100L,
                content = "Valid message"
            )
            
            Then("isValid()가 true를 반환해야 한다") {
                validMessage.isValid() shouldBe true
            }
        }
        
        When("roomId가 0 이하이면") {
            val invalidMessage = ChatMessage(
                roomId = 0L,
                userId = 100L,
                content = "Valid message"
            )
            
            Then("isValid()가 false를 반환해야 한다") {
                invalidMessage.isValid() shouldBe false
            }
        }
        
        When("userId가 0 이하이면") {
            val invalidMessage = ChatMessage(
                roomId = 1L,
                userId = -1L,
                content = "Valid message"
            )
            
            Then("isValid()가 false를 반환해야 한다") {
                invalidMessage.isValid() shouldBe false
            }
        }
        
        When("content가 빈 문자열이면") {
            val invalidMessage = ChatMessage(
                roomId = 1L,
                userId = 100L,
                content = ""
            )
            
            Then("isValid()가 false를 반환해야 한다") {
                invalidMessage.isValid() shouldBe false
            }
        }
        
        When("content가 공백만 있으면") {
            val invalidMessage = ChatMessage(
                roomId = 1L,
                userId = 100L,
                content = "   "
            )
            
            Then("isValid()가 false를 반환해야 한다") {
                invalidMessage.isValid() shouldBe false
            }
        }
        
        When("content가 1000자를 초과하면") {
            val longContent = "a".repeat(1001)
            val invalidMessage = ChatMessage(
                roomId = 1L,
                userId = 100L,
                content = longContent
            )
            
            Then("isValid()가 false를 반환해야 한다") {
                invalidMessage.isValid() shouldBe false
            }
        }
        
        When("content가 정확히 1000자이면") {
            val maxContent = "a".repeat(1000)
            val validMessage = ChatMessage(
                roomId = 1L,
                userId = 100L,
                content = maxContent
            )
            
            Then("isValid()가 true를 반환해야 한다") {
                validMessage.isValid() shouldBe true
            }
        }
    }
    
    Given("메시지 타입 확인 시") {
        When("messageType이 TEXT이면") {
            val textMessage = ChatMessage(
                roomId = 1L,
                userId = 100L,
                content = "Hello",
                messageType = MessageType.TEXT
            )
            
            Then("isUserMessage()가 true여야 한다") {
                textMessage.isUserMessage() shouldBe true
            }
            
            Then("isSystemMessage()가 false여야 한다") {
                textMessage.isSystemMessage() shouldBe false
            }
        }
        
        When("messageType이 SYSTEM이면") {
            val systemMessage = ChatMessage(
                roomId = 1L,
                userId = 0L, // 시스템 메시지는 userId가 0일 수 있음
                content = "System notification",
                messageType = MessageType.SYSTEM
            )
            
            Then("isSystemMessage()가 true여야 한다") {
                systemMessage.isSystemMessage() shouldBe true
            }
            
            Then("isUserMessage()가 false여야 한다") {
                systemMessage.isUserMessage() shouldBe false
            }
        }
        
        When("messageType이 JOIN이면") {
            val joinMessage = ChatMessage(
                roomId = 1L,
                userId = 100L,
                content = "User joined",
                messageType = MessageType.JOIN
            )
            
            Then("isSystemMessage()가 true여야 한다") {
                joinMessage.isSystemMessage() shouldBe true
            }
            
            Then("isUserMessage()가 false여야 한다") {
                joinMessage.isUserMessage() shouldBe false
            }
        }
        
        When("messageType이 LEAVE이면") {
            val leaveMessage = ChatMessage(
                roomId = 1L,
                userId = 100L,
                content = "User left",
                messageType = MessageType.LEAVE
            )
            
            Then("isSystemMessage()가 true여야 한다") {
                leaveMessage.isSystemMessage() shouldBe true
            }
            
            Then("isUserMessage()가 false여야 한다") {
                leaveMessage.isUserMessage() shouldBe false
            }
        }
    }
    
    Given("메시지 정보 출력 시") {
        val message = ChatMessage(
            id = "msg-123",
            roomId = 1L,
            userId = 100L,
            content = "Test message",
            messageType = MessageType.TEXT
        )
        
        When("getMessageInfo()를 호출하면") {
            val info = message.getMessageInfo()
            
            Then("필요한 정보들이 포함되어야 한다") {
                info shouldContain "roomId=1"
                info shouldContain "userId=100"
                info shouldContain "type=TEXT"
                info shouldContain "Message("
            }
        }
        
        When("toString()을 호출하면") {
            val string = message.toString()
            
            Then("모든 주요 필드가 포함되어야 한다") {
                string shouldContain "ChatMessage("
                string shouldContain "id=msg-123"
                string shouldContain "roomId=1"
                string shouldContain "userId=100"
                string shouldContain "messageType=TEXT"
            }
        }
    }
    
    Given("equals와 hashCode 테스트 시") {
        val message1 = ChatMessage(
            id = "same-id",
            roomId = 1L,
            userId = 100L,
            content = "Message 1"
        )
        
        val message2 = ChatMessage(
            id = "same-id",
            roomId = 2L, // 다른 roomId
            userId = 200L, // 다른 userId
            content = "Message 2" // 다른 content
        )
        
        val message3 = ChatMessage(
            id = "different-id",
            roomId = 1L,
            userId = 100L,
            content = "Message 1"
        )
        
        val messageWithoutId = ChatMessage(
            id = null,
            roomId = 1L,
            userId = 100L,
            content = "No ID message"
        )
        
        When("같은 ID를 가진 메시지들이면") {
            Then("equals()가 true여야 한다") {
                message1 shouldBe message2
            }
            
            Then("hashCode()가 같아야 한다") {
                message1.hashCode() shouldBe message2.hashCode()
            }
        }
        
        When("다른 ID를 가진 메시지들이면") {
            Then("equals()가 false여야 한다") {
                message1 shouldNotBe message3
            }
        }
        
        When("ID가 null인 메시지들이면") {
            val anotherMessageWithoutId = ChatMessage(
                id = null,
                roomId = 2L,
                userId = 200L,
                content = "Another no ID message"
            )
            
            Then("equals()가 false여야 한다") {
                messageWithoutId shouldNotBe anotherMessageWithoutId
            }
            
            Then("hashCode()가 0이어야 한다") {
                messageWithoutId.hashCode() shouldBe 0
            }
        }
        
        When("자기 자신과 비교하면") {
            Then("equals()가 true여야 한다") {
                message1 shouldBe message1
            }
        }
        
        When("null과 비교하면") {
            Then("equals()가 false여야 한다") {
                message1.equals(null) shouldBe false
            }
        }
        
        When("다른 타입의 객체와 비교하면") {
            Then("equals()가 false여야 한다") {
                message1.equals("not a ChatMessage") shouldBe false
            }
        }
    }
    
    Given("속성 기반 테스트 시") {
        When("임의의 유효한 값들로 메시지를 생성하면") {
            checkAll(
                iterations = 100,
                Arb.positiveInt(max = 1000).map { it.toLong() }, // roomId
                Arb.positiveInt(max = 10000).map { it.toLong() }, // userId
                Arb.string(minSize = 1, maxSize = 1000).filter { it.isNotBlank() }, // content
                Arb.enum<MessageType>() // messageType
            ) { roomId, userId, content, messageType ->
                val message = ChatMessage(
                    roomId = roomId,
                    userId = userId,
                    content = content,
                    messageType = messageType
                )
                
                // 모든 유효한 입력에 대해 isValid()가 true여야 함
                message.isValid() shouldBe true
                
                // 메시지 타입 분류가 올바르게 작동해야 함
                when (messageType) {
                    MessageType.TEXT -> {
                        message.isUserMessage() shouldBe true
                        message.isSystemMessage() shouldBe false
                    }
                    MessageType.SYSTEM, MessageType.JOIN, MessageType.LEAVE -> {
                        message.isSystemMessage() shouldBe true
                        message.isUserMessage() shouldBe false
                    }
                }
            }
        }
        
        When("임의의 무효한 값들로 메시지를 생성하면") {
            checkAll(
                iterations = 50,
                Arb.choice(
                    Arb.nonPositiveInt().map { it.toLong() }, // 0 이하의 roomId
                    Arb.positiveInt(max = 1000).map { it.toLong() } // 유효한 roomId (다른 필드가 무효할 때)
                ),
                Arb.choice(
                    Arb.nonPositiveInt().map { it.toLong() }, // 0 이하의 userId
                    Arb.positiveInt(max = 10000).map { it.toLong() } // 유효한 userId
                ),
                Arb.choice(
                    Arb.constant(""), // 빈 문자열
                    Arb.string(1001..2000), // 너무 긴 문자열
                    Arb.int(1..10).map { " ".repeat(it) } // 공백만 있는 문자열
                )
            ) { roomId, userId, content ->
                val message = ChatMessage(
                    roomId = roomId,
                    userId = userId,
                    content = content
                )
                
                // 무효한 입력이 하나라도 있으면 isValid()가 false여야 함
                val shouldBeValid = roomId > 0 && userId > 0 && content.isNotBlank() && content.length <= 1000
                message.isValid() shouldBe shouldBeValid
            }
        }
    }
})