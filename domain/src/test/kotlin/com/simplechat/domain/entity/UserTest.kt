package com.simplechat.domain.entity

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime

/**
 * User 도메인 엔티티 테스트
 * 
 * 순수한 도메인 로직만 테스트하며, 외부 의존성 없이 검증합니다.
 */
class UserTest : BehaviorSpec({
    
    Given("유효한 사용자 정보가 주어졌을 때") {
        val validEmail = "test@example.com"
        val validPasswordHash = "hashedPassword123"
        val validNickname = "testuser"
        
        When("사용자를 생성하면") {
            val user = User(
                email = validEmail,
                passwordHash = validPasswordHash,
                nickname = validNickname
            )
            
            Then("올바른 사용자가 생성되어야 한다") {
                user.email shouldBe validEmail
                user.passwordHash shouldBe validPasswordHash
                user.nickname shouldBe validNickname
                user.id shouldBe null
                user.createdAt shouldNotBe null
            }
        }
        
        When("사용자 유효성을 검증하면") {
            val user = User(
                email = validEmail,
                passwordHash = validPasswordHash,
                nickname = validNickname
            )
            
            Then("유효하다고 판단되어야 한다") {
                user.isValid() shouldBe true
            }
        }
    }
    
    Given("잘못된 사용자 정보가 주어졌을 때") {
        When("이메일이 비어있으면") {
            val user = User(
                email = "",
                passwordHash = "hashedPassword123",
                nickname = "testuser"
            )
            
            Then("유효하지 않다고 판단되어야 한다") {
                user.isValid() shouldBe false
            }
        }
        
        When("이메일에 @가 없으면") {
            val user = User(
                email = "invalid-email",
                passwordHash = "hashedPassword123",
                nickname = "testuser"
            )
            
            Then("유효하지 않다고 판단되어야 한다") {
                user.isValid() shouldBe false
            }
        }
        
        When("비밀번호 해시가 비어있으면") {
            val user = User(
                email = "test@example.com",
                passwordHash = "",
                nickname = "testuser"
            )
            
            Then("유효하지 않다고 판단되어야 한다") {
                user.isValid() shouldBe false
            }
        }
        
        When("닉네임이 너무 짧으면") {
            val user = User(
                email = "test@example.com",
                passwordHash = "hashedPassword123",
                nickname = "a"
            )
            
            Then("유효하지 않다고 판단되어야 한다") {
                user.isValid() shouldBe false
            }
        }
        
        When("닉네임이 너무 길면") {
            val user = User(
                email = "test@example.com",
                passwordHash = "hashedPassword123",
                nickname = "a".repeat(51)
            )
            
            Then("유효하지 않다고 판단되어야 한다") {
                user.isValid() shouldBe false
            }
        }
    }
    
    Given("비밀번호 검증 시") {
        val user = User(
            email = "test@example.com",
            passwordHash = "correctHash",
            nickname = "testuser"
        )
        
        When("올바른 해시 비밀번호로 검증하면") {
            Then("참을 반환해야 한다") {
                user.isPasswordValid("correctHash") shouldBe true
            }
        }
        
        When("잘못된 해시 비밀번호로 검증하면") {
            Then("거짓을 반환해야 한다") {
                user.isPasswordValid("wrongHash") shouldBe false
            }
        }
    }
    
    Given("표시명 생성 시") {
        When("닉네임이 있으면") {
            val user = User(
                email = "test@example.com",
                passwordHash = "hash",
                nickname = "MyNickname"
            )
            
            Then("닉네임을 반환해야 한다") {
                user.getDisplayName() shouldBe "MyNickname"
            }
        }
        
        When("닉네임이 비어있으면") {
            val user = User(
                email = "test@example.com",
                passwordHash = "hash",
                nickname = ""
            )
            
            Then("이메일의 @ 앞부분을 반환해야 한다") {
                user.getDisplayName() shouldBe "test"
            }
        }
    }
    
    Given("동등성 비교 시") {
        val userId = 1L
        val user1 = User(
            id = userId,
            email = "test1@example.com",
            passwordHash = "hash1",
            nickname = "user1"
        )
        val user2 = User(
            id = userId,
            email = "test2@example.com",
            passwordHash = "hash2",
            nickname = "user2"
        )
        val user3 = User(
            id = 2L,
            email = "test1@example.com",
            passwordHash = "hash1",
            nickname = "user1"
        )
        
        When("같은 ID를 가진 사용자들을 비교하면") {
            Then("동등하다고 판단되어야 한다") {
                user1 shouldBe user2
                user1.hashCode() shouldBe user2.hashCode()
            }
        }
        
        When("다른 ID를 가진 사용자들을 비교하면") {
            Then("동등하지 않다고 판단되어야 한다") {
                user1 shouldNotBe user3
            }
        }
        
        When("ID가 null인 사용자들을 비교하면") {
            val userWithoutId1 = User(
                email = "test@example.com",
                passwordHash = "hash",
                nickname = "user"
            )
            val userWithoutId2 = User(
                email = "test@example.com",
                passwordHash = "hash",
                nickname = "user"
            )
            
            Then("동등하지 않다고 판단되어야 한다") {
                userWithoutId1 shouldNotBe userWithoutId2
            }
        }
    }
    
    Given("toString 테스트") {
        When("사용자 정보를 문자열로 변환하면") {
            val user = User(
                id = 1L,
                email = "test@example.com",
                passwordHash = "hash",
                nickname = "testuser",
                createdAt = LocalDateTime.of(2023, 1, 1, 12, 0, 0)
            )
            
            Then("올바른 형식의 문자열이 반환되어야 한다") {
                val result = user.toString()
                result shouldBe "User(id=1, email='test@example.com', nickname='testuser', createdAt=2023-01-01T12:00)"
            }
        }
    }
})