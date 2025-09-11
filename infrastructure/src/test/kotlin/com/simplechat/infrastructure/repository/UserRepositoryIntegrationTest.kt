package com.simplechat.infrastructure.repository

import com.simplechat.domain.entity.User
import com.simplechat.infrastructure.config.TestContainersConfig
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.test.runTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest
import org.springframework.context.annotation.Import
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.test.context.ActiveProfiles
import reactor.test.StepVerifier

/**
 * UserRepository 통합 테스트
 * 
 * Testcontainers PostgreSQL을 사용한 실제 데이터베이스 환경에서 테스트합니다.
 */
@DataR2dbcTest
@ActiveProfiles("test")
@Import(TestContainersConfig::class, UserRepositoryImpl::class)
class UserRepositoryIntegrationTest : BehaviorSpec() {
    
    override fun extensions() = listOf(SpringExtension)
    
    @Autowired
    private lateinit var userRepository: UserRepositoryImpl
    
    @Autowired
    private lateinit var r2dbcEntityTemplate: R2dbcEntityTemplate
    
    init {
        Given("PostgreSQL 테스트 컨테이너가 실행 중일 때") {
            
            beforeEach {
                // 각 테스트 전에 테이블 정리
                r2dbcEntityTemplate.databaseClient
                    .sql("TRUNCATE TABLE users RESTART IDENTITY CASCADE")
                    .then()
                    .awaitSingleOrNull()
            }
            
            When("새로운 사용자를 저장하면") {
                val user = User(
                    email = "test@example.com",
                    passwordHash = "hashedPassword123",
                    nickname = "testuser"
                )
                
                Then("데이터베이스에 저장되고 ID가 할당되어야 한다") {
                    runTest {
                        val savedUser = userRepository.save(user).awaitSingle()
                        
                        savedUser.id shouldNotBe null
                        savedUser.email shouldBe user.email
                        savedUser.passwordHash shouldBe user.passwordHash
                        savedUser.nickname shouldBe user.nickname
                        savedUser.createdAt shouldNotBe null
                    }
                }
            }
            
            When("이메일로 사용자를 조회하면") {
                val user = User(
                    email = "findme@example.com",
                    passwordHash = "hashedPassword123",
                    nickname = "findme"
                )
                
                Then("올바른 사용자가 반환되어야 한다") {
                    runTest {
                        val savedUser = userRepository.save(user).awaitSingle()
                        val foundUser = userRepository.findByEmail("findme@example.com").awaitSingle()
                        
                        foundUser.id shouldBe savedUser.id
                        foundUser.email shouldBe user.email
                        foundUser.nickname shouldBe user.nickname
                    }
                }
            }
            
            When("존재하지 않는 이메일로 조회하면") {
                Then("결과가 없어야 한다") {
                    StepVerifier.create(userRepository.findByEmail("notfound@example.com"))
                        .expectComplete()
                        .verify()
                }
            }
            
            When("닉네임으로 사용자를 조회하면") {
                val user = User(
                    email = "nickname@example.com", 
                    passwordHash = "hashedPassword123",
                    nickname = "uniquenick"
                )
                
                Then("올바른 사용자가 반환되어야 한다") {
                    runTest {
                        val savedUser = userRepository.save(user).awaitSingle()
                        val foundUser = userRepository.findByNickname("uniquenick").awaitSingle()
                        
                        foundUser.id shouldBe savedUser.id
                        foundUser.email shouldBe user.email
                        foundUser.nickname shouldBe user.nickname
                    }
                }
            }
            
            When("이메일 존재 여부를 확인하면") {
                val user = User(
                    email = "exists@example.com",
                    passwordHash = "hashedPassword123", 
                    nickname = "existsuser"
                )
                
                Then("저장된 이메일은 true, 저장되지 않은 이메일은 false를 반환해야 한다") {
                    runTest {
                        userRepository.save(user).awaitSingle()
                        
                        val exists = userRepository.existsByEmail("exists@example.com").awaitSingle()
                        val notExists = userRepository.existsByEmail("notexists@example.com").awaitSingle()
                        
                        exists shouldBe true
                        notExists shouldBe false
                    }
                }
            }
            
            When("닉네임 존재 여부를 확인하면") {
                val user = User(
                    email = "nicktest@example.com",
                    passwordHash = "hashedPassword123",
                    nickname = "existsnick"
                )
                
                Then("저장된 닉네임은 true, 저장되지 않은 닉네임은 false를 반환해야 한다") {
                    runTest {
                        userRepository.save(user).awaitSingle()
                        
                        val exists = userRepository.existsByNickname("existsnick").awaitSingle()
                        val notExists = userRepository.existsByNickname("notexistsnick").awaitSingle()
                        
                        exists shouldBe true
                        notExists shouldBe false
                    }
                }
            }
            
            When("이메일 또는 닉네임으로 사용자를 조회하면") {
                val user1 = User(
                    email = "user1@example.com",
                    passwordHash = "hashedPassword123",
                    nickname = "user1nick"
                )
                val user2 = User(
                    email = "user2@example.com", 
                    passwordHash = "hashedPassword123",
                    nickname = "user2nick"
                )
                
                Then("이메일이나 닉네임 중 하나라도 일치하면 사용자가 반환되어야 한다") {
                    runTest {
                        userRepository.save(user1).awaitSingle()
                        userRepository.save(user2).awaitSingle()
                        
                        // 이메일로 조회
                        val foundByEmail = userRepository.findByEmailOrNickname("user1@example.com", "nonexistent").awaitSingle()
                        foundByEmail.email shouldBe user1.email
                        
                        // 닉네임으로 조회
                        val foundByNickname = userRepository.findByEmailOrNickname("nonexistent@example.com", "user2nick").awaitSingle()
                        foundByNickname.nickname shouldBe user2.nickname
                    }
                }
            }
            
            When("최근 사용자들을 조회하면") {
                val users = listOf(
                    User(email = "recent1@example.com", passwordHash = "hash1", nickname = "recent1"),
                    User(email = "recent2@example.com", passwordHash = "hash2", nickname = "recent2"),
                    User(email = "recent3@example.com", passwordHash = "hash3", nickname = "recent3")
                )
                
                Then("생성일 순으로 정렬된 사용자 목록이 반환되어야 한다") {
                    runTest {
                        // 순차적으로 저장하여 생성 시간 차이 보장
                        for (user in users) {
                            userRepository.save(user).awaitSingle()
                            Thread.sleep(10) // 생성 시간 차이를 위한 짧은 대기
                        }
                        
                        // 최근 2명만 조회
                        val recentUsers = userRepository.findRecentUsers(2)
                            .collectList()
                            .awaitSingle()
                        
                        recentUsers.size shouldBe 2
                        // 최근 순으로 정렬되어야 함 (recent3, recent2)
                        recentUsers[0].nickname shouldBe "recent3"
                        recentUsers[1].nickname shouldBe "recent2"
                    }
                }
            }
            
            When("사용자 수를 조회하면") {
                val users = listOf(
                    User(email = "count1@example.com", passwordHash = "hash1", nickname = "count1"),
                    User(email = "count2@example.com", passwordHash = "hash2", nickname = "count2")
                )
                
                Then("정확한 사용자 수가 반환되어야 한다") {
                    runTest {
                        for (user in users) {
                            userRepository.save(user).awaitSingle()
                        }
                        
                        val count = userRepository.countUsers().awaitSingle()
                        count shouldBe 2L
                    }
                }
            }
            
            When("사용자를 삭제하면") {
                val user = User(
                    email = "delete@example.com",
                    passwordHash = "hashedPassword123",
                    nickname = "deleteuser"
                )
                
                Then("데이터베이스에서 제거되어야 한다") {
                    runTest {
                        val savedUser = userRepository.save(user).awaitSingle()
                        userRepository.deleteById(savedUser.id!!).awaitSingleOrNull()
                        
                        val exists = userRepository.existsById(savedUser.id!!).awaitSingle()
                        exists shouldBe false
                    }
                }
            }
        }
    }
}