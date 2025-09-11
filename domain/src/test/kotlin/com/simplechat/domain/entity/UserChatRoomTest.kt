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
 * UserChatRoom 엔티티 도메인 로직 테스트
 * 
 * 사용자-채팅방 관계의 비즈니스 로직과 권한 관리를 테스트합니다.
 */
class UserChatRoomTest : BehaviorSpec({
    
    Given("UserChatRoom 생성 시") {
        val userId = 100L
        val chatRoomId = 1L
        val joinTime = LocalDateTime.now()
        
        When("기본값으로 생성하면") {
            val userChatRoom = UserChatRoom(
                userId = userId,
                chatRoomId = chatRoomId
            )
            
            Then("기본값들이 올바르게 설정되어야 한다") {
                userChatRoom.userId shouldBe userId
                userChatRoom.chatRoomId shouldBe chatRoomId
                userChatRoom.role shouldBe ChatRoomRole.MEMBER
                userChatRoom.isActive shouldBe true
                userChatRoom.lastReadAt shouldBe null
                userChatRoom.isMuted shouldBe false
                userChatRoom.isPinned shouldBe false
                userChatRoom.leftAt shouldBe null
                userChatRoom.invitedBy shouldBe null
                userChatRoom.joinedAt shouldNotBe null
                userChatRoom.updatedAt shouldNotBe null
            }
        }
        
        When("모든 값을 지정하여 생성하면") {
            val readTime = joinTime.plusHours(1)
            val updateTime = joinTime.plusHours(2)
            val inviterId = 200L
            
            val userChatRoom = UserChatRoom(
                userId = userId,
                chatRoomId = chatRoomId,
                role = ChatRoomRole.ADMIN,
                joinedAt = joinTime,
                isActive = true,
                lastReadAt = readTime,
                isMuted = true,
                isPinned = true,
                leftAt = null,
                invitedBy = inviterId,
                updatedAt = updateTime
            )
            
            Then("모든 속성이 올바르게 설정되어야 한다") {
                userChatRoom.userId shouldBe userId
                userChatRoom.chatRoomId shouldBe chatRoomId
                userChatRoom.role shouldBe ChatRoomRole.ADMIN
                userChatRoom.joinedAt shouldBe joinTime
                userChatRoom.isActive shouldBe true
                userChatRoom.lastReadAt shouldBe readTime
                userChatRoom.isMuted shouldBe true
                userChatRoom.isPinned shouldBe true
                userChatRoom.leftAt shouldBe null
                userChatRoom.invitedBy shouldBe inviterId
                userChatRoom.updatedAt shouldBe updateTime
            }
        }
    }
    
    Given("UserChatRoom 유효성 검증 시") {
        When("모든 필드가 유효하면") {
            val validUserChatRoom = UserChatRoom(
                userId = 100L,
                chatRoomId = 1L
            )
            
            Then("isValid()가 true를 반환해야 한다") {
                validUserChatRoom.isValid() shouldBe true
            }
        }
        
        When("userId가 0 이하이면") {
            val invalidUserChatRoom = UserChatRoom(
                userId = 0L,
                chatRoomId = 1L
            )
            
            Then("isValid()가 false를 반환해야 한다") {
                invalidUserChatRoom.isValid() shouldBe false
            }
        }
        
        When("chatRoomId가 0 이하이면") {
            val invalidUserChatRoom = UserChatRoom(
                userId = 100L,
                chatRoomId = -1L
            )
            
            Then("isValid()가 false를 반환해야 한다") {
                invalidUserChatRoom.isValid() shouldBe false
            }
        }
        
        When("leftAt이 joinedAt보다 이전이면") {
            val joinTime = LocalDateTime.now()
            val leftTime = joinTime.minusHours(1)
            
            val invalidUserChatRoom = UserChatRoom(
                userId = 100L,
                chatRoomId = 1L,
                joinedAt = joinTime,
                leftAt = leftTime
            )
            
            Then("isValid()가 false를 반환해야 한다") {
                invalidUserChatRoom.isValid() shouldBe false
            }
        }
        
        When("leftAt이 joinedAt과 같거나 이후이면") {
            val joinTime = LocalDateTime.now()
            val leftTime = joinTime.plusHours(1)
            
            val validUserChatRoom = UserChatRoom(
                userId = 100L,
                chatRoomId = 1L,
                joinedAt = joinTime,
                leftAt = leftTime
            )
            
            Then("isValid()가 true를 반환해야 한다") {
                validUserChatRoom.isValid() shouldBe true
            }
        }
    }
    
    Given("사용자 참여 상태 확인 시") {
        When("활성 상태이고 나가지 않았으면") {
            val userChatRoom = UserChatRoom(
                userId = 100L,
                chatRoomId = 1L,
                isActive = true,
                leftAt = null
            )
            
            Then("isActiveParticipant()가 true여야 한다") {
                userChatRoom.isActiveParticipant() shouldBe true
            }
            
            Then("hasLeft()가 false여야 한다") {
                userChatRoom.hasLeft() shouldBe false
            }
        }
        
        When("비활성 상태이면") {
            val userChatRoom = UserChatRoom(
                userId = 100L,
                chatRoomId = 1L,
                isActive = false,
                leftAt = null
            )
            
            Then("isActiveParticipant()가 false여야 한다") {
                userChatRoom.isActiveParticipant() shouldBe false
            }
        }
        
        When("채팅방을 나갔으면") {
            val userChatRoom = UserChatRoom(
                userId = 100L,
                chatRoomId = 1L,
                isActive = true,
                leftAt = LocalDateTime.now()
            )
            
            Then("isActiveParticipant()가 false여야 한다") {
                userChatRoom.isActiveParticipant() shouldBe false
            }
            
            Then("hasLeft()가 true여야 한다") {
                userChatRoom.hasLeft() shouldBe true
            }
        }
    }
    
    Given("권한 확인 테스트 시") {
        When("MEMBER 역할일 때") {
            val memberUser = UserChatRoom(
                userId = 100L,
                chatRoomId = 1L,
                role = ChatRoomRole.MEMBER
            )
            
            Then("관리자 권한이 없어야 한다") {
                memberUser.hasAdminPrivileges() shouldBe false
                memberUser.isOwner() shouldBe false
                memberUser.canModifyRoomSettings() shouldBe false
                memberUser.canDeleteRoom() shouldBe false
            }
            
            Then("다른 멤버의 역할을 변경할 수 없어야 한다") {
                memberUser.canChangeRoleOf(ChatRoomRole.MEMBER) shouldBe false
                memberUser.canKickUser(ChatRoomRole.MEMBER) shouldBe false
            }
        }
        
        When("ADMIN 역할일 때") {
            val adminUser = UserChatRoom(
                userId = 100L,
                chatRoomId = 1L,
                role = ChatRoomRole.ADMIN
            )
            
            Then("관리자 권한이 있어야 한다") {
                adminUser.hasAdminPrivileges() shouldBe true
                adminUser.canModifyRoomSettings() shouldBe true
            }
            
            Then("소유자 권한은 없어야 한다") {
                adminUser.isOwner() shouldBe false
                adminUser.canDeleteRoom() shouldBe false
            }
            
            Then("일반 멤버의 역할을 변경할 수 있어야 한다") {
                adminUser.canChangeRoleOf(ChatRoomRole.MEMBER) shouldBe true
                adminUser.canKickUser(ChatRoomRole.MEMBER) shouldBe true
            }
            
            Then("같은 관리자나 소유자의 역할은 변경할 수 없어야 한다") {
                adminUser.canChangeRoleOf(ChatRoomRole.ADMIN) shouldBe false
                adminUser.canChangeRoleOf(ChatRoomRole.OWNER) shouldBe false
            }
        }
        
        When("OWNER 역할일 때") {
            val ownerUser = UserChatRoom(
                userId = 100L,
                chatRoomId = 1L,
                role = ChatRoomRole.OWNER
            )
            
            Then("모든 권한이 있어야 한다") {
                ownerUser.hasAdminPrivileges() shouldBe true
                ownerUser.isOwner() shouldBe true
                ownerUser.canModifyRoomSettings() shouldBe true
                ownerUser.canDeleteRoom() shouldBe true
            }
            
            Then("모든 역할의 사용자를 관리할 수 있어야 한다") {
                ownerUser.canChangeRoleOf(ChatRoomRole.MEMBER) shouldBe true
                ownerUser.canChangeRoleOf(ChatRoomRole.ADMIN) shouldBe true
                ownerUser.canKickUser(ChatRoomRole.MEMBER) shouldBe true
                ownerUser.canKickUser(ChatRoomRole.ADMIN) shouldBe true
            }
            
            Then("다른 소유자의 역할은 변경할 수 없어야 한다") {
                ownerUser.canChangeRoleOf(ChatRoomRole.OWNER) shouldBe false
            }
        }
    }
    
    Given("상태 변경 메서드 테스트 시") {
        val baseUserChatRoom = UserChatRoom(
            userId = 100L,
            chatRoomId = 1L,
            role = ChatRoomRole.MEMBER,
            isActive = true,
            isMuted = false,
            isPinned = false
        )
        
        When("leave() 메서드를 호출하면") {
            val leftUserChatRoom = baseUserChatRoom.leave()
            
            Then("나가기 상태로 변경되어야 한다") {
                leftUserChatRoom.isActive shouldBe false
                leftUserChatRoom.leftAt shouldNotBe null
                leftUserChatRoom.updatedAt shouldNotBe baseUserChatRoom.updatedAt
            }
            
            Then("원본 객체는 변경되지 않아야 한다") {
                baseUserChatRoom.isActive shouldBe true
                baseUserChatRoom.leftAt shouldBe null
            }
        }
        
        When("rejoin() 메서드를 호출하면") {
            val rejoinedUserChatRoom = baseUserChatRoom.rejoin()
            
            Then("다시 활성 상태로 변경되어야 한다") {
                rejoinedUserChatRoom.isActive shouldBe true
                rejoinedUserChatRoom.leftAt shouldBe null
                rejoinedUserChatRoom.joinedAt shouldNotBe baseUserChatRoom.joinedAt
                rejoinedUserChatRoom.updatedAt shouldNotBe baseUserChatRoom.updatedAt
            }
        }
        
        When("changeRole() 메서드를 호출하면") {
            val newRole = ChatRoomRole.ADMIN
            val changedUserChatRoom = baseUserChatRoom.changeRole(newRole)
            
            Then("역할이 변경되어야 한다") {
                changedUserChatRoom.role shouldBe newRole
                changedUserChatRoom.updatedAt shouldNotBe baseUserChatRoom.updatedAt
            }
            
            Then("원본 객체는 변경되지 않아야 한다") {
                baseUserChatRoom.role shouldBe ChatRoomRole.MEMBER
            }
        }
        
        When("markAsRead() 메서드를 호출하면") {
            val readTime = LocalDateTime.now().plusMinutes(30)
            val readUserChatRoom = baseUserChatRoom.markAsRead(readTime)
            
            Then("읽음 시간이 설정되어야 한다") {
                readUserChatRoom.lastReadAt shouldBe readTime
                readUserChatRoom.updatedAt shouldNotBe baseUserChatRoom.updatedAt
            }
        }
        
        When("toggleMute() 메서드를 호출하면") {
            val mutedUserChatRoom = baseUserChatRoom.toggleMute()
            
            Then("음소거 상태가 토글되어야 한다") {
                mutedUserChatRoom.isMuted shouldBe true
                mutedUserChatRoom.updatedAt shouldNotBe baseUserChatRoom.updatedAt
            }
            
            When("다시 toggleMute()를 호출하면") {
                val unmutedUserChatRoom = mutedUserChatRoom.toggleMute()
                
                Then("음소거가 해제되어야 한다") {
                    unmutedUserChatRoom.isMuted shouldBe false
                }
            }
        }
        
        When("togglePin() 메서드를 호출하면") {
            val pinnedUserChatRoom = baseUserChatRoom.togglePin()
            
            Then("고정 상태가 토글되어야 한다") {
                pinnedUserChatRoom.isPinned shouldBe true
                pinnedUserChatRoom.updatedAt shouldNotBe baseUserChatRoom.updatedAt
            }
            
            When("다시 togglePin()을 호출하면") {
                val unpinnedUserChatRoom = pinnedUserChatRoom.togglePin()
                
                Then("고정이 해제되어야 한다") {
                    unpinnedUserChatRoom.isPinned shouldBe false
                }
            }
        }
    }
    
    Given("참여 기간 계산 테스트 시") {
        val joinTime = LocalDateTime.of(2024, 1, 1, 10, 0)
        
        When("아직 나가지 않은 상태라면") {
            val userChatRoom = UserChatRoom(
                userId = 100L,
                chatRoomId = 1L,
                joinedAt = joinTime,
                leftAt = null
            )
            
            // 현재 시간과 비교하므로 실제 날짜 차이를 계산
            Then("참여 기간이 계산되어야 한다") {
                val participationDays = userChatRoom.getParticipationDays()
                // 2024년 1월 1일부터 현재까지의 일수이므로 양수여야 함
                (participationDays >= 0) shouldBe true
            }
        }
        
        When("7일 참여 후 나갔다면") {
            val leftTime = joinTime.plusDays(7)
            val userChatRoom = UserChatRoom(
                userId = 100L,
                chatRoomId = 1L,
                joinedAt = joinTime,
                leftAt = leftTime
            )
            
            Then("참여 기간이 7일이어야 한다") {
                userChatRoom.getParticipationDays() shouldBe 7
            }
        }
        
        When("동일한 날에 가입하고 나갔다면") {
            val userChatRoom = UserChatRoom(
                userId = 100L,
                chatRoomId = 1L,
                joinedAt = joinTime,
                leftAt = joinTime.plusHours(5)  // 같은 날 나중에 나감
            )
            
            Then("참여 기간이 0일이어야 한다") {
                userChatRoom.getParticipationDays() shouldBe 0
            }
        }
    }
    
    Given("읽지 않은 메시지 확인 테스트 시") {
        val baseTime = LocalDateTime.now()
        val readTime = baseTime.minusHours(1)
        val updateTime = baseTime
        
        When("마지막 읽음 시간이 null이면") {
            val userChatRoom = UserChatRoom(
                userId = 100L,
                chatRoomId = 1L,
                lastReadAt = null,
                updatedAt = updateTime
            )
            
            Then("읽지 않은 메시지가 있을 가능성이 있어야 한다") {
                userChatRoom.mayHaveUnreadMessages() shouldBe true
            }
        }
        
        When("마지막 읽음 시간이 업데이트 시간보다 이전이면") {
            val userChatRoom = UserChatRoom(
                userId = 100L,
                chatRoomId = 1L,
                lastReadAt = readTime,
                updatedAt = updateTime
            )
            
            Then("읽지 않은 메시지가 있을 가능성이 있어야 한다") {
                userChatRoom.mayHaveUnreadMessages() shouldBe true
            }
        }
        
        When("마지막 읽음 시간이 업데이트 시간과 같거나 이후라면") {
            val userChatRoom = UserChatRoom(
                userId = 100L,
                chatRoomId = 1L,
                lastReadAt = updateTime,
                updatedAt = readTime  // 읽음 시간이 더 늦음
            )
            
            Then("읽지 않은 메시지가 없을 가능성이 높아야 한다") {
                userChatRoom.mayHaveUnreadMessages() shouldBe false
            }
        }
    }
    
    Given("equals와 hashCode 테스트 시") {
        val userChatRoom1 = UserChatRoom(
            userId = 100L,
            chatRoomId = 1L,
            role = ChatRoomRole.MEMBER
        )
        
        val userChatRoom2 = UserChatRoom(
            userId = 100L,
            chatRoomId = 1L,
            role = ChatRoomRole.ADMIN  // 다른 역할
        )
        
        val userChatRoom3 = UserChatRoom(
            userId = 200L,  // 다른 사용자
            chatRoomId = 1L,
            role = ChatRoomRole.MEMBER
        )
        
        val userChatRoom4 = UserChatRoom(
            userId = 100L,
            chatRoomId = 2L,  // 다른 채팅방
            role = ChatRoomRole.MEMBER
        )
        
        When("같은 userId와 chatRoomId를 가진 객체들이면") {
            Then("equals()가 true여야 한다") {
                userChatRoom1 shouldBe userChatRoom2
            }
            
            Then("hashCode()가 같아야 한다") {
                userChatRoom1.hashCode() shouldBe userChatRoom2.hashCode()
            }
        }
        
        When("다른 userId를 가진 객체들이면") {
            Then("equals()가 false여야 한다") {
                userChatRoom1 shouldNotBe userChatRoom3
            }
        }
        
        When("다른 chatRoomId를 가진 객체들이면") {
            Then("equals()가 false여야 한다") {
                userChatRoom1 shouldNotBe userChatRoom4
            }
        }
        
        When("자기 자신과 비교하면") {
            Then("equals()가 true여야 한다") {
                userChatRoom1 shouldBe userChatRoom1
            }
        }
        
        When("null과 비교하면") {
            Then("equals()가 false여야 한다") {
                userChatRoom1.equals(null) shouldBe false
            }
        }
        
        When("다른 타입의 객체와 비교하면") {
            Then("equals()가 false여야 한다") {
                userChatRoom1.equals("not a UserChatRoom") shouldBe false
            }
        }
    }
    
    Given("toString 테스트 시") {
        val userChatRoom = UserChatRoom(
            userId = 100L,
            chatRoomId = 1L,
            role = ChatRoomRole.ADMIN,
            isActive = true
        )
        
        When("toString()을 호출하면") {
            val string = userChatRoom.toString()
            
            Then("주요 필드들이 포함되어야 한다") {
                string shouldContain "UserChatRoom("
                string shouldContain "userId=100"
                string shouldContain "chatRoomId=1"
                string shouldContain "role=ADMIN"
                string shouldContain "isActive=true"
            }
        }
    }
    
    Given("속성 기반 테스트 시") {
        When("임의의 유효한 값들로 UserChatRoom을 생성하면") {
            checkAll(
                iterations = 100,
                Arb.positiveInt(max = 10000).map { it.toLong() }, // userId
                Arb.positiveInt(max = 1000).map { it.toLong() }, // chatRoomId
                Arb.enum<ChatRoomRole>(), // role
                Arb.boolean(), // isActive
                Arb.boolean(), // isMuted
                Arb.boolean() // isPinned
            ) { userId, chatRoomId, role, isActive, isMuted, isPinned ->
                val userChatRoom = UserChatRoom(
                    userId = userId,
                    chatRoomId = chatRoomId,
                    role = role,
                    isActive = isActive,
                    isMuted = isMuted,
                    isPinned = isPinned
                )
                
                // 유효한 입력에 대해 isValid()가 true여야 함
                userChatRoom.isValid() shouldBe true
                
                // 역할에 따른 권한이 올바르게 작동해야 함
                when (role) {
                    ChatRoomRole.MEMBER -> {
                        userChatRoom.hasAdminPrivileges() shouldBe false
                        userChatRoom.isOwner() shouldBe false
                    }
                    ChatRoomRole.ADMIN -> {
                        userChatRoom.hasAdminPrivileges() shouldBe true
                        userChatRoom.isOwner() shouldBe false
                    }
                    ChatRoomRole.OWNER -> {
                        userChatRoom.hasAdminPrivileges() shouldBe true
                        userChatRoom.isOwner() shouldBe true
                    }
                }
                
                // 동등성이 userId와 chatRoomId에만 의존해야 함
                val sameRelation = UserChatRoom(
                    userId = userId,
                    chatRoomId = chatRoomId,
                    role = ChatRoomRole.values().random(),
                    isActive = !isActive
                )
                userChatRoom shouldBe sameRelation
            }
        }
        
        When("무효한 ID들로 UserChatRoom을 생성하면") {
            checkAll(
                iterations = 50,
                Arb.choice(
                    Arb.nonPositiveInt().map { it.toLong() }, // 0 이하의 userId
                    Arb.positiveInt(max = 10000).map { it.toLong() } // 유효한 userId
                ),
                Arb.choice(
                    Arb.nonPositiveInt().map { it.toLong() }, // 0 이하의 chatRoomId
                    Arb.positiveInt(max = 1000).map { it.toLong() } // 유효한 chatRoomId
                )
            ) { userId, chatRoomId ->
                val userChatRoom = UserChatRoom(
                    userId = userId,
                    chatRoomId = chatRoomId
                )
                
                // 무효한 ID가 하나라도 있으면 isValid()가 false여야 함
                val shouldBeValid = userId > 0 && chatRoomId > 0
                userChatRoom.isValid() shouldBe shouldBeValid
            }
        }
    }
})