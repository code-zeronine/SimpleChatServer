package com.simplechat.domain.entity

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime

/**
 * ChatRoom 도메인 엔티티 테스트
 * 
 * 순수한 도메인 로직만 테스트하며, 외부 의존성 없이 검증합니다.
 */
class ChatRoomTest : BehaviorSpec({
    
    Given("유효한 채팅방 정보가 주어졌을 때") {
        val validName = "테스트 채팅방"
        val validDescription = "테스트용 채팅방입니다"
        val createdBy = 1L
        
        When("채팅방을 생성하면") {
            val chatRoom = ChatRoom(
                name = validName,
                description = validDescription,
                createdBy = createdBy
            )
            
            Then("올바른 채팅방이 생성되어야 한다") {
                chatRoom.name shouldBe validName
                chatRoom.description shouldBe validDescription
                chatRoom.createdBy shouldBe createdBy
                chatRoom.id shouldBe null
                chatRoom.isPrivate shouldBe false
                chatRoom.maxParticipants shouldBe 100
                chatRoom.createdAt shouldNotBe null
                chatRoom.updatedAt shouldNotBe null
            }
        }
        
        When("채팅방 유효성을 검증하면") {
            val chatRoom = ChatRoom(
                name = validName,
                description = validDescription,
                createdBy = createdBy
            )
            
            Then("유효하다고 판단되어야 한다") {
                chatRoom.isValid() shouldBe true
            }
        }
    }
    
    Given("잘못된 채팅방 정보가 주어졌을 때") {
        val createdBy = 1L
        
        When("이름이 비어있으면") {
            val chatRoom = ChatRoom(
                name = "",
                createdBy = createdBy
            )
            
            Then("유효하지 않다고 판단되어야 한다") {
                chatRoom.isValid() shouldBe false
            }
        }
        
        When("이름이 너무 길면") {
            val chatRoom = ChatRoom(
                name = "a".repeat(101),
                createdBy = createdBy
            )
            
            Then("유효하지 않다고 판단되어야 한다") {
                chatRoom.isValid() shouldBe false
            }
        }
        
        When("설명이 너무 길면") {
            val chatRoom = ChatRoom(
                name = "테스트방",
                description = "a".repeat(501),
                createdBy = createdBy
            )
            
            Then("유효하지 않다고 판단되어야 한다") {
                chatRoom.isValid() shouldBe false
            }
        }
        
        When("최대 참여자 수가 0 이하면") {
            val chatRoom = ChatRoom(
                name = "테스트방",
                createdBy = createdBy,
                maxParticipants = 0
            )
            
            Then("유효하지 않다고 판단되어야 한다") {
                chatRoom.isValid() shouldBe false
            }
        }
        
        When("최대 참여자 수가 시스템 제한을 초과하면") {
            val chatRoom = ChatRoom(
                name = "테스트방",
                createdBy = createdBy,
                maxParticipants = 1001
            )
            
            Then("유효하지 않다고 판단되어야 한다") {
                chatRoom.isValid() shouldBe false
            }
        }
    }
    
    Given("소유권 검증 시") {
        val ownerId = 1L
        val otherUserId = 2L
        val chatRoom = ChatRoom(
            name = "테스트방",
            createdBy = ownerId
        )
        
        When("소유자가 확인하면") {
            Then("참을 반환해야 한다") {
                chatRoom.isOwnedBy(ownerId) shouldBe true
            }
        }
        
        When("다른 사용자가 확인하면") {
            Then("거짓을 반환해야 한다") {
                chatRoom.isOwnedBy(otherUserId) shouldBe false
            }
        }
    }
    
    Given("비공개 채팅방 확인 시") {
        When("공개 채팅방이면") {
            val chatRoom = ChatRoom(
                name = "공개방",
                createdBy = 1L,
                isPrivate = false
            )
            
            Then("거짓을 반환해야 한다") {
                chatRoom.isPrivateRoom() shouldBe false
            }
        }
        
        When("비공개 채팅방이면") {
            val chatRoom = ChatRoom(
                name = "비공개방",
                createdBy = 1L,
                isPrivate = true
            )
            
            Then("참을 반환해야 한다") {
                chatRoom.isPrivateRoom() shouldBe true
            }
        }
    }
    
    Given("참여자 수 제한 확인 시") {
        val chatRoom = ChatRoom(
            name = "테스트방",
            createdBy = 1L,
            maxParticipants = 5
        )
        
        When("현재 참여자 수가 제한보다 적으면") {
            Then("여유가 있다고 판단되어야 한다") {
                chatRoom.isAtCapacity(4) shouldBe false
            }
        }
        
        When("현재 참여자 수가 제한과 같으면") {
            Then("정원에 도달했다고 판단되어야 한다") {
                chatRoom.isAtCapacity(5) shouldBe true
            }
        }
        
        When("현재 참여자 수가 제한보다 많으면") {
            Then("정원을 초과했다고 판단되어야 한다") {
                chatRoom.isAtCapacity(6) shouldBe true
            }
        }
    }
    
    Given("표시명 생성 시") {
        When("이름이 정상적으로 있으면") {
            val chatRoom = ChatRoom(
                name = "  테스트방  ",
                createdBy = 1L
            )
            
            Then("트림된 이름을 반환해야 한다") {
                chatRoom.getDisplayName() shouldBe "테스트방"
            }
        }
        
        When("이름이 비어있으면") {
            val chatRoom = ChatRoom(
                name = "   ",
                createdBy = 1L
            )
            
            Then("기본명을 반환해야 한다") {
                chatRoom.getDisplayName() shouldBe "이름 없는 채팅방"
            }
        }
    }
    
    Given("채팅방 정보 업데이트 시") {
        val originalChatRoom = ChatRoom(
            name = "원래방",
            description = "원래설명",
            createdBy = 1L,
            maxParticipants = 50
        )
        
        When("이름만 업데이트하면") {
            val updated = originalChatRoom.updateInfo(newName = "새로운방")
            
            Then("이름만 변경되고 나머지는 유지되어야 한다") {
                updated.name shouldBe "새로운방"
                updated.description shouldBe originalChatRoom.description
                updated.maxParticipants shouldBe originalChatRoom.maxParticipants
                updated.updatedAt shouldNotBe originalChatRoom.updatedAt
            }
        }
        
        When("설명만 업데이트하면") {
            val updated = originalChatRoom.updateInfo(newDescription = "새로운설명")
            
            Then("설명만 변경되고 나머지는 유지되어야 한다") {
                updated.name shouldBe originalChatRoom.name
                updated.description shouldBe "새로운설명"
                updated.maxParticipants shouldBe originalChatRoom.maxParticipants
                updated.updatedAt shouldNotBe originalChatRoom.updatedAt
            }
        }
        
        When("최대 참여자 수만 업데이트하면") {
            val updated = originalChatRoom.updateInfo(newMaxParticipants = 200)
            
            Then("최대 참여자 수만 변경되고 나머지는 유지되어야 한다") {
                updated.name shouldBe originalChatRoom.name
                updated.description shouldBe originalChatRoom.description
                updated.maxParticipants shouldBe 200
                updated.updatedAt shouldNotBe originalChatRoom.updatedAt
            }
        }
        
        When("잘못된 값으로 업데이트하면") {
            val updated = originalChatRoom.updateInfo(
                newName = "",
                newMaxParticipants = -1
            )
            
            Then("원래 값이 유지되어야 한다") {
                updated.name shouldBe originalChatRoom.name
                updated.maxParticipants shouldBe originalChatRoom.maxParticipants
            }
        }
        
        When("시스템 제한을 초과하는 값으로 업데이트하면") {
            val updated = originalChatRoom.updateInfo(newMaxParticipants = 1001)
            
            Then("원래 값이 유지되어야 한다") {
                updated.maxParticipants shouldBe originalChatRoom.maxParticipants
            }
        }
    }
    
    Given("동등성 비교 시") {
        val chatRoomId = 1L
        val chatRoom1 = ChatRoom(
            id = chatRoomId,
            name = "방1",
            createdBy = 1L
        )
        val chatRoom2 = ChatRoom(
            id = chatRoomId,
            name = "방2",
            createdBy = 2L
        )
        val chatRoom3 = ChatRoom(
            id = 2L,
            name = "방1",
            createdBy = 1L
        )
        
        When("같은 ID를 가진 채팅방들을 비교하면") {
            Then("동등하다고 판단되어야 한다") {
                chatRoom1 shouldBe chatRoom2
                chatRoom1.hashCode() shouldBe chatRoom2.hashCode()
            }
        }
        
        When("다른 ID를 가진 채팅방들을 비교하면") {
            Then("동등하지 않다고 판단되어야 한다") {
                chatRoom1 shouldNotBe chatRoom3
            }
        }
    }
    
    Given("toString 테스트") {
        When("채팅방 정보를 문자열로 변환하면") {
            val chatRoom = ChatRoom(
                id = 1L,
                name = "테스트방",
                createdBy = 100L,
                isPrivate = true,
                createdAt = LocalDateTime.of(2023, 1, 1, 12, 0, 0)
            )
            
            Then("올바른 형식의 문자열이 반환되어야 한다") {
                val result = chatRoom.toString()
                result shouldBe "ChatRoom(id=1, name='테스트방', createdBy=100, isPrivate=true, createdAt=2023-01-01T12:00)"
            }
        }
    }
})