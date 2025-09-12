package com.simplechat.domain.constants

import com.simplechat.domain.entity.ChatRoomRole
import com.simplechat.domain.entity.MessageType
import com.simplechat.domain.message.MessageTargetType
import com.simplechat.domain.message.websocket.DuplicateLoginAction
import com.simplechat.domain.message.websocket.SessionTerminationReason
import com.simplechat.domain.message.websocket.WebSocketMessageType
import com.simplechat.domain.service.RoutingStrategy
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

/**
 * Enum 및 상수 클래스들에 대한 포괄적인 테스트
 * 
 * 테스트 대상:
 * - 모든 Enum 클래스들
 * - 상수 객체 (RedisChannelConstants)
 * - Enum의 비즈니스 로직 메서드들
 * - 상수 클래스의 유틸리티 메서드들
 */
class EnumAndConstantsTest : BehaviorSpec({

    Given("RoutingStrategy enum 테스트") {
        When("모든 라우팅 전략을 확인하면") {
            val allStrategies = RoutingStrategy.entries
            
            Then("정의된 모든 전략이 존재해야 한다") {
                allStrategies shouldHaveSize 6
                allStrategies shouldContain RoutingStrategy.BROADCAST_TO_ROOM
                allStrategies shouldContain RoutingStrategy.SEND_TO_USER
                allStrategies shouldContain RoutingStrategy.SEND_TO_USERS
                allStrategies shouldContain RoutingStrategy.SEND_TO_SESSION
                allStrategies shouldContain RoutingStrategy.BROADCAST_GLOBALLY
                allStrategies shouldContain RoutingStrategy.CUSTOM
            }
        }

        When("toString()을 호출하면") {
            Then("Enum 이름이 반환되어야 한다") {
                RoutingStrategy.BROADCAST_TO_ROOM.toString() shouldBe "BROADCAST_TO_ROOM"
                RoutingStrategy.SEND_TO_USER.toString() shouldBe "SEND_TO_USER"
                RoutingStrategy.CUSTOM.toString() shouldBe "CUSTOM"
            }
        }
    }

    Given("DuplicateLoginAction enum 테스트") {
        When("모든 중복 로그인 행동을 확인하면") {
            val allActions = DuplicateLoginAction.entries
            
            Then("정의된 모든 행동이 존재해야 한다") {
                allActions shouldHaveSize 4
                allActions shouldContain DuplicateLoginAction.NOTIFY_ONLY
                allActions shouldContain DuplicateLoginAction.FORCE_LOGOUT_OTHERS
                allActions shouldContain DuplicateLoginAction.FORCE_LOGOUT_CURRENT
                allActions shouldContain DuplicateLoginAction.ASK_USER_CHOICE
            }
        }

        When("각 행동의 이름을 확인하면") {
            Then("올바른 이름을 가져야 한다") {
                DuplicateLoginAction.NOTIFY_ONLY.name shouldBe "NOTIFY_ONLY"
                DuplicateLoginAction.FORCE_LOGOUT_OTHERS.name shouldBe "FORCE_LOGOUT_OTHERS"
                DuplicateLoginAction.FORCE_LOGOUT_CURRENT.name shouldBe "FORCE_LOGOUT_CURRENT"
                DuplicateLoginAction.ASK_USER_CHOICE.name shouldBe "ASK_USER_CHOICE"
            }
        }
    }

    Given("MessageTargetType enum 테스트") {
        When("모든 메시지 타겟 타입을 확인하면") {
            val allTypes = MessageTargetType.entries
            
            Then("정의된 모든 타입이 존재해야 한다") {
                allTypes shouldHaveSize 4
                allTypes shouldContain MessageTargetType.USER
                allTypes shouldContain MessageTargetType.ROOM
                allTypes shouldContain MessageTargetType.SESSION
                allTypes shouldContain MessageTargetType.GLOBAL
            }
        }

        When("ordinal 값을 확인하면") {
            Then("순서가 올바르게 정의되어야 한다") {
                MessageTargetType.USER.ordinal shouldBe 0
                MessageTargetType.ROOM.ordinal shouldBe 1
                MessageTargetType.SESSION.ordinal shouldBe 2
                MessageTargetType.GLOBAL.ordinal shouldBe 3
            }
        }
    }

    Given("MessageType enum 테스트") {
        When("모든 메시지 타입을 확인하면") {
            val allTypes = MessageType.entries
            
            Then("정의된 모든 타입이 존재해야 한다") {
                allTypes shouldHaveSize 4
                allTypes shouldContain MessageType.TEXT
                allTypes shouldContain MessageType.SYSTEM
                allTypes shouldContain MessageType.JOIN
                allTypes shouldContain MessageType.LEAVE
            }
        }
    }

    Given("ChannelType enum 테스트") {
        When("모든 채널 타입을 확인하면") {
            val allTypes = ChannelType.entries
            
            Then("정의된 모든 타입이 존재해야 한다") {
                allTypes shouldHaveSize 6
                allTypes shouldContain ChannelType.CHAT_ROOM
                allTypes shouldContain ChannelType.USER_PRIVATE
                allTypes shouldContain ChannelType.GLOBAL
                allTypes shouldContain ChannelType.SYSTEM
                allTypes shouldContain ChannelType.ADMIN
                allTypes shouldContain ChannelType.CUSTOM
            }
        }
    }

    Given("ChatRoomRole enum 테스트") {
        When("모든 역할을 확인하면") {
            val allRoles = ChatRoomRole.entries
            
            Then("정의된 모든 역할이 존재해야 한다") {
                allRoles shouldHaveSize 3
                allRoles shouldContain ChatRoomRole.MEMBER
                allRoles shouldContain ChatRoomRole.ADMIN
                allRoles shouldContain ChatRoomRole.OWNER
            }
        }

        When("역할별 속성을 확인하면") {
            Then("MEMBER 역할이 올바른 속성을 가져야 한다") {
                ChatRoomRole.MEMBER.displayName shouldBe "멤버"
                ChatRoomRole.MEMBER.description shouldBe "일반 채팅 참여자"
                ChatRoomRole.MEMBER.level shouldBe 1
            }
            
            Then("ADMIN 역할이 올바른 속성을 가져야 한다") {
                ChatRoomRole.ADMIN.displayName shouldBe "관리자"
                ChatRoomRole.ADMIN.description shouldBe "채팅방 관리 권한 보유"
                ChatRoomRole.ADMIN.level shouldBe 2
            }
            
            Then("OWNER 역할이 올바른 속성을 가져야 한다") {
                ChatRoomRole.OWNER.displayName shouldBe "소유자"
                ChatRoomRole.OWNER.description shouldBe "채팅방 소유자 (모든 권한)"
                ChatRoomRole.OWNER.level shouldBe 3
            }
        }

        When("권한 비교 메서드를 테스트하면") {
            Then("hasHigherAuthorityThan()이 올바르게 동작해야 한다") {
                ChatRoomRole.OWNER.hasHigherAuthorityThan(ChatRoomRole.ADMIN) shouldBe true
                ChatRoomRole.ADMIN.hasHigherAuthorityThan(ChatRoomRole.MEMBER) shouldBe true
                ChatRoomRole.MEMBER.hasHigherAuthorityThan(ChatRoomRole.OWNER) shouldBe false
                ChatRoomRole.ADMIN.hasHigherAuthorityThan(ChatRoomRole.ADMIN) shouldBe false
            }
            
            Then("hasAuthorityEqualOrHigherThan()이 올바르게 동작해야 한다") {
                ChatRoomRole.OWNER.hasAuthorityEqualOrHigherThan(ChatRoomRole.ADMIN) shouldBe true
                ChatRoomRole.ADMIN.hasAuthorityEqualOrHigherThan(ChatRoomRole.ADMIN) shouldBe true
                ChatRoomRole.MEMBER.hasAuthorityEqualOrHigherThan(ChatRoomRole.ADMIN) shouldBe false
            }
        }

        When("역할 확인 메서드를 테스트하면") {
            Then("isAdminOrAbove()가 올바르게 동작해야 한다") {
                ChatRoomRole.OWNER.isAdminOrAbove() shouldBe true
                ChatRoomRole.ADMIN.isAdminOrAbove() shouldBe true
                ChatRoomRole.MEMBER.isAdminOrAbove() shouldBe false
            }
            
            Then("isOwner()가 올바르게 동작해야 한다") {
                ChatRoomRole.OWNER.isOwner() shouldBe true
                ChatRoomRole.ADMIN.isOwner() shouldBe false
                ChatRoomRole.MEMBER.isOwner() shouldBe false
            }
            
            Then("isMember()가 올바르게 동작해야 한다") {
                ChatRoomRole.MEMBER.isMember() shouldBe true
                ChatRoomRole.ADMIN.isMember() shouldBe false
                ChatRoomRole.OWNER.isMember() shouldBe false
            }
        }

        When("동반 객체 메서드를 테스트하면") {
            Then("fromString()이 올바르게 동작해야 한다") {
                ChatRoomRole.fromString("MEMBER") shouldBe ChatRoomRole.MEMBER
                ChatRoomRole.fromString("admin") shouldBe ChatRoomRole.ADMIN
                ChatRoomRole.fromString("Owner") shouldBe ChatRoomRole.OWNER
                ChatRoomRole.fromString("INVALID") shouldBe null
                ChatRoomRole.fromString(null) shouldBe null
            }
            
            Then("fromLevel()이 올바르게 동작해야 한다") {
                ChatRoomRole.fromLevel(1) shouldBe ChatRoomRole.MEMBER
                ChatRoomRole.fromLevel(2) shouldBe ChatRoomRole.ADMIN
                ChatRoomRole.fromLevel(3) shouldBe ChatRoomRole.OWNER
                ChatRoomRole.fromLevel(0) shouldBe null
                ChatRoomRole.fromLevel(4) shouldBe null
            }
            
            Then("defaultRole()이 올바른 기본 역할을 반환해야 한다") {
                ChatRoomRole.defaultRole() shouldBe ChatRoomRole.MEMBER
            }
            
            Then("getAllRolesByLevel()이 레벨 순으로 정렬된 역할을 반환해야 한다") {
                val sortedRoles = ChatRoomRole.getAllRolesByLevel()
                sortedRoles shouldHaveSize 3
                sortedRoles[0] shouldBe ChatRoomRole.MEMBER
                sortedRoles[1] shouldBe ChatRoomRole.ADMIN
                sortedRoles[2] shouldBe ChatRoomRole.OWNER
            }
        }
    }

    Given("RedisChannelConstants 상수 테스트") {
        When("채널 접두사 상수들을 확인하면") {
            Then("정의된 접두사들이 올바른 값을 가져야 한다") {
                RedisChannelConstants.CHAT_ROOM_PREFIX shouldBe "chat:room:"
                RedisChannelConstants.USER_PRIVATE_PREFIX shouldBe "user:private:"
                RedisChannelConstants.GLOBAL_CHANNEL shouldBe "chat:global"
                RedisChannelConstants.SYSTEM_CHANNEL shouldBe "system:notifications"
                RedisChannelConstants.ADMIN_CHANNEL shouldBe "admin:control"
            }
        }

        When("제약 조건 상수들을 확인하면") {
            Then("유효한 제약 값들이 정의되어야 한다") {
                RedisChannelConstants.MAX_CHANNEL_NAME_LENGTH shouldBe 100
                RedisChannelConstants.MIN_ROOM_ID shouldBe 1L
                RedisChannelConstants.MAX_ROOM_ID shouldBe 999999999L
                RedisChannelConstants.MIN_USER_ID shouldBe 1L
                RedisChannelConstants.MAX_USER_ID shouldBe 999999999L
            }
        }

        When("채팅방 채널명을 생성하면") {
            Then("올바른 형식의 채널명이 생성되어야 한다") {
                RedisChannelConstants.createRoomChannelName(123L) shouldBe "chat:room:123"
                RedisChannelConstants.createRoomChannelName(1L) shouldBe "chat:room:1"
                RedisChannelConstants.createRoomChannelName(999999999L) shouldBe "chat:room:999999999"
            }
            
            Then("유효하지 않은 방 ID에 대해 예외가 발생해야 한다") {
                shouldThrow<IllegalArgumentException> {
                    RedisChannelConstants.createRoomChannelName(0L)
                }
                shouldThrow<IllegalArgumentException> {
                    RedisChannelConstants.createRoomChannelName(-1L)
                }
                shouldThrow<IllegalArgumentException> {
                    RedisChannelConstants.createRoomChannelName(1000000000L)
                }
            }
        }

        When("사용자 개인 채널명을 생성하면") {
            Then("올바른 형식의 채널명이 생성되어야 한다") {
                RedisChannelConstants.createUserPrivateChannelName(456L) shouldBe "user:private:456"
                RedisChannelConstants.createUserPrivateChannelName(1L) shouldBe "user:private:1"
                RedisChannelConstants.createUserPrivateChannelName(999999999L) shouldBe "user:private:999999999"
            }
            
            Then("유효하지 않은 사용자 ID에 대해 예외가 발생해야 한다") {
                shouldThrow<IllegalArgumentException> {
                    RedisChannelConstants.createUserPrivateChannelName(0L)
                }
                shouldThrow<IllegalArgumentException> {
                    RedisChannelConstants.createUserPrivateChannelName(-1L)
                }
                shouldThrow<IllegalArgumentException> {
                    RedisChannelConstants.createUserPrivateChannelName(1000000000L)
                }
            }
        }

        When("채널명에서 방 ID를 추출하면") {
            Then("올바른 방 ID가 추출되어야 한다") {
                RedisChannelConstants.extractRoomIdFromChannel("chat:room:123") shouldBe 123L
                RedisChannelConstants.extractRoomIdFromChannel("chat:room:1") shouldBe 1L
                RedisChannelConstants.extractRoomIdFromChannel("chat:room:999999999") shouldBe 999999999L
            }
            
            Then("잘못된 채널명에서는 null이 반환되어야 한다") {
                RedisChannelConstants.extractRoomIdFromChannel("user:private:123") shouldBe null
                RedisChannelConstants.extractRoomIdFromChannel("chat:room:abc") shouldBe null
                RedisChannelConstants.extractRoomIdFromChannel("chat:room:0") shouldBe null
                RedisChannelConstants.extractRoomIdFromChannel("chat:room:1000000000") shouldBe null
                RedisChannelConstants.extractRoomIdFromChannel("invalid:channel") shouldBe null
            }
        }

        When("채널명에서 사용자 ID를 추출하면") {
            Then("올바른 사용자 ID가 추출되어야 한다") {
                RedisChannelConstants.extractUserIdFromChannel("user:private:456") shouldBe 456L
                RedisChannelConstants.extractUserIdFromChannel("user:private:1") shouldBe 1L
                RedisChannelConstants.extractUserIdFromChannel("user:private:999999999") shouldBe 999999999L
            }
            
            Then("잘못된 채널명에서는 null이 반환되어야 한다") {
                RedisChannelConstants.extractUserIdFromChannel("chat:room:123") shouldBe null
                RedisChannelConstants.extractUserIdFromChannel("user:private:abc") shouldBe null
                RedisChannelConstants.extractUserIdFromChannel("user:private:0") shouldBe null
                RedisChannelConstants.extractUserIdFromChannel("user:private:1000000000") shouldBe null
                RedisChannelConstants.extractUserIdFromChannel("invalid:channel") shouldBe null
            }
        }

        When("채널 타입을 식별하면") {
            Then("각 채널명에 대해 올바른 타입이 식별되어야 한다") {
                RedisChannelConstants.identifyChannelType("chat:global") shouldBe ChannelType.GLOBAL
                RedisChannelConstants.identifyChannelType("system:notifications") shouldBe ChannelType.SYSTEM
                RedisChannelConstants.identifyChannelType("admin:control") shouldBe ChannelType.ADMIN
                RedisChannelConstants.identifyChannelType("chat:room:123") shouldBe ChannelType.CHAT_ROOM
                RedisChannelConstants.identifyChannelType("user:private:456") shouldBe ChannelType.USER_PRIVATE
                RedisChannelConstants.identifyChannelType("custom:channel") shouldBe ChannelType.CUSTOM
            }
        }

        When("채널명 유효성을 검사하면") {
            Then("유효한 채널명들이 통과해야 한다") {
                RedisChannelConstants.isValidChannelName("chat:room:123") shouldBe true
                RedisChannelConstants.isValidChannelName("user:private:456") shouldBe true
                RedisChannelConstants.isValidChannelName("chat:global") shouldBe true
                RedisChannelConstants.isValidChannelName("system.notifications") shouldBe true
                RedisChannelConstants.isValidChannelName("admin_control") shouldBe true
                RedisChannelConstants.isValidChannelName("custom-channel") shouldBe true
            }
            
            Then("유효하지 않은 채널명들이 거부되어야 한다") {
                RedisChannelConstants.isValidChannelName("") shouldBe false
                RedisChannelConstants.isValidChannelName(" ") shouldBe false
                RedisChannelConstants.isValidChannelName("a".repeat(101)) shouldBe false
                RedisChannelConstants.isValidChannelName("channel with spaces") shouldBe false
                RedisChannelConstants.isValidChannelName("channel@invalid") shouldBe false
                RedisChannelConstants.isValidChannelName("channel#invalid") shouldBe false
                RedisChannelConstants.isValidChannelName("한글채널") shouldBe false
            }
        }
    }

    Given("WebSocketMessageType enum 테스트") {
        When("모든 WebSocket 메시지 타입을 확인하면") {
            val allTypes = WebSocketMessageType.entries
            
            Then("정의된 모든 타입이 존재해야 한다") {
                allTypes.size shouldBeGreaterThan 0
                allTypes shouldContain WebSocketMessageType.CHAT
                allTypes shouldContain WebSocketMessageType.JOIN
                allTypes shouldContain WebSocketMessageType.LEAVE
                allTypes shouldContain WebSocketMessageType.TYPING
                allTypes shouldContain WebSocketMessageType.HEARTBEAT
                allTypes shouldContain WebSocketMessageType.SYSTEM
                allTypes shouldContain WebSocketMessageType.ERROR
                allTypes shouldContain WebSocketMessageType.ACK
            }
        }
    }

    Given("SessionTerminationReason enum 테스트") {
        When("모든 세션 종료 이유를 확인하면") {
            val allReasons = SessionTerminationReason.entries
            
            Then("정의된 모든 이유가 존재해야 한다") {
                allReasons shouldHaveSize 6
                allReasons shouldContain SessionTerminationReason.DUPLICATE_LOGIN
                allReasons shouldContain SessionTerminationReason.FORCED_LOGOUT
                allReasons shouldContain SessionTerminationReason.SECURITY_VIOLATION
                allReasons shouldContain SessionTerminationReason.INACTIVITY_TIMEOUT
                allReasons shouldContain SessionTerminationReason.USER_REQUEST
                allReasons shouldContain SessionTerminationReason.SYSTEM_MAINTENANCE
            }
        }
    }

    Given("Enum 공통 특성 테스트") {
        When("모든 Enum들의 values() 메서드를 호출하면") {
            Then("빈 배열이 반환되지 않아야 한다") {
                RoutingStrategy.entries.shouldNotBeEmpty()
                DuplicateLoginAction.entries.shouldNotBeEmpty()
                MessageTargetType.entries.shouldNotBeEmpty()
                MessageType.entries.shouldNotBeEmpty()
                ChannelType.entries.shouldNotBeEmpty()
                ChatRoomRole.entries.shouldNotBeEmpty()
            }
        }

        When("Enum의 name 속성을 확인하면") {
            Then("상수 이름과 일치해야 한다") {
                RoutingStrategy.BROADCAST_TO_ROOM.name shouldBe "BROADCAST_TO_ROOM"
                MessageTargetType.USER.name shouldBe "USER"
                ChatRoomRole.ADMIN.name shouldBe "ADMIN"
            }
        }

        When("Enum의 ordinal 속성을 확인하면") {
            Then("0부터 시작하는 순서 값이어야 한다") {
                val messageTypes = MessageType.entries
                messageTypes.forEachIndexed { index, type ->
                    type.ordinal shouldBe index
                }
            }
        }
    }
})