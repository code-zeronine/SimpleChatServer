package com.simplechat.service

import com.simplechat.domain.entity.User
import com.simplechat.domain.exception.ErrorCode
import com.simplechat.domain.exception.ValidationException
import com.simplechat.domain.exception.auth.AuthenticationException
import com.simplechat.domain.repository.UserRepository
import com.simplechat.dto.auth.LoginRequest
import com.simplechat.dto.auth.SignUpRequest
import com.simplechat.infrastructure.config.DuplicateLoginConfig
import com.simplechat.infrastructure.config.DuplicateLoginConfigAction
import com.simplechat.infrastructure.config.JwtProperties
import com.simplechat.infrastructure.security.jwt.JwtSessionService
import com.simplechat.infrastructure.security.jwt.JwtTokenProvider
import com.simplechat.infrastructure.session.WebSocketSessionManager
import com.simplechat.infrastructure.websocket.handler.WebSocketMessageHandler
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.springframework.security.crypto.password.PasswordEncoder
import reactor.core.publisher.Mono
import java.time.LocalDateTime

/**
 * AuthService 단위 테스트
 * 
 * MockK를 사용하여 의존성을 모킹하고 비즈니스 로직만 테스트합니다.
 */
class AuthServiceTest : BehaviorSpec({
    
    // Mock 객체들
    val userRepository = mockk<UserRepository>()
    val passwordEncoder = mockk<PasswordEncoder>()
    val jwtTokenProvider = mockk<JwtTokenProvider>()
    val jwtProperties = mockk<JwtProperties>()
    val sessionInvalidationService = mockk<SessionInvalidationService>()
    val webSocketSessionManager = mockk<WebSocketSessionManager>()
    val webSocketMessageHandler = mockk<WebSocketMessageHandler>()
    val duplicateLoginConfig = mockk<DuplicateLoginConfig>()
    val jwtSessionService = mockk<JwtSessionService>()
    
    // 테스트 대상 서비스
    val authService = AuthService(
        userRepository = userRepository,
        passwordEncoder = passwordEncoder,
        jwtTokenProvider = jwtTokenProvider,
        jwtProperties = jwtProperties,
        sessionInvalidationService = sessionInvalidationService,
        webSocketSessionManager = webSocketSessionManager,
        webSocketMessageHandler = webSocketMessageHandler,
        duplicateLoginConfig = duplicateLoginConfig,
        jwtSessionService = jwtSessionService
    )
    
    Given("회원가입 처리 시") {
        val signUpRequest = SignUpRequest(
            email = "test@example.com",
            password = "password123",
            nickname = "testuser"
        )
        
        When("유효한 회원가입 요청이 주어지면") {
            coEvery { userRepository.existsByEmail(any()) } returns Mono.just(false)
            coEvery { userRepository.existsByNickname(any()) } returns Mono.just(false)
            every { passwordEncoder.encode(any()) } returns "hashedPassword123"
            
            val savedUser = User(
                id = 1L,
                email = signUpRequest.email,
                passwordHash = "hashedPassword123",
                nickname = signUpRequest.nickname,
                createdAt = LocalDateTime.now()
            )
            coEvery { userRepository.save(any()) } returns Mono.just(savedUser)
            
            every { jwtTokenProvider.generateAccessToken(any(), any(), any()) } returns "access.token.here"
            every { jwtTokenProvider.generateRefreshToken(any(), any(), any()) } returns "refresh.token.here"
            every { jwtProperties.expiration } returns 3600000L
            every { jwtProperties.refreshExpiration } returns 86400000L
            
            Then("회원가입이 성공하고 토큰이 발급되어야 한다") {
                runTest {
                    val response = authService.signUp(signUpRequest)
                    
                    response.accessToken.shouldNotBeEmpty()
                    response.refreshToken.shouldNotBeEmpty()
                    response.user.email shouldBe signUpRequest.email
                    response.user.nickname shouldBe signUpRequest.nickname
                    response.user.id shouldBe 1L
                    
                    coVerify { userRepository.existsByEmail(signUpRequest.email) }
                    coVerify { userRepository.existsByNickname(signUpRequest.nickname) }
                    coVerify { userRepository.save(any()) }
                }
            }
        }
        
        When("이미 존재하는 이메일로 회원가입을 시도하면") {
            coEvery { userRepository.existsByEmail(any()) } returns Mono.just(true)
            
            Then("ValidationException이 발생해야 한다") {
                runTest {
                    val exception = shouldThrow<ValidationException> {
                        authService.signUp(signUpRequest)
                    }
                    exception.errorCode shouldBe ErrorCode.DUPLICATE_EMAIL
                }
            }
        }
        
        When("이미 존재하는 닉네임으로 회원가입을 시도하면") {
            coEvery { userRepository.existsByEmail(any()) } returns Mono.just(false)
            coEvery { userRepository.existsByNickname(any()) } returns Mono.just(true)
            
            Then("ValidationException이 발생해야 한다") {
                runTest {
                    val exception = shouldThrow<ValidationException> {
                        authService.signUp(signUpRequest)
                    }
                    exception.errorCode shouldBe ErrorCode.DUPLICATE_NICKNAME
                }
            }
        }
        
    }
    
    Given("로그인 처리 시") {
        val loginRequest = LoginRequest(
            email = "test@example.com",
            password = "password123"
        )
        
        val existingUser = User(
            id = 1L,
            email = loginRequest.email,
            passwordHash = "hashedPassword123",
            nickname = "testuser",
            createdAt = LocalDateTime.now()
        )
        
        When("유효한 로그인 요청이 주어지면") {
            coEvery { userRepository.findByEmail(any()) } returns Mono.just(existingUser)
            every { passwordEncoder.matches(any(), any()) } returns true
            every { duplicateLoginConfig.enabled } returns false
            
            every { jwtTokenProvider.generateAccessToken(any(), any(), any()) } returns "access.token.here"
            every { jwtTokenProvider.generateRefreshToken(any(), any(), any()) } returns "refresh.token.here"
            every { jwtProperties.expiration } returns 3600000L
            every { jwtProperties.refreshExpiration } returns 86400000L
            
            Then("로그인이 성공하고 토큰이 발급되어야 한다") {
                runTest {
                    val response = authService.login(loginRequest)
                    
                    response.accessToken.shouldNotBeEmpty()
                    response.refreshToken.shouldNotBeEmpty()
                    response.user.email shouldBe existingUser.email
                    response.user.nickname shouldBe existingUser.nickname
                    response.user.id shouldBe existingUser.id
                    
                    coVerify { userRepository.findByEmail(loginRequest.email) }
                }
            }
        }
        
        When("존재하지 않는 사용자로 로그인을 시도하면") {
            coEvery { userRepository.findByEmail(any()) } returns Mono.empty()
            
            Then("AuthenticationException이 발생해야 한다") {
                runTest {
                    val exception = shouldThrow<AuthenticationException> {
                        authService.login(loginRequest)
                    }
                    exception.errorCode shouldBe ErrorCode.AUTHENTICATION_FAILED
                }
            }
        }
        
        When("잘못된 비밀번호로 로그인을 시도하면") {
            coEvery { userRepository.findByEmail(any()) } returns Mono.just(existingUser)
            every { passwordEncoder.matches(any(), any()) } returns false
            
            Then("AuthenticationException이 발생해야 한다") {
                runTest {
                    val exception = shouldThrow<AuthenticationException> {
                        authService.login(loginRequest)
                    }
                    exception.errorCode shouldBe ErrorCode.AUTHENTICATION_FAILED
                }
            }
        }
    }
    
    Given("중복 로그인 제어가 활성화된 상황에서") {
        val loginRequest = LoginRequest(
            email = "test@example.com",
            password = "password123"
        )
        
        val existingUser = User(
            id = 1L,
            email = loginRequest.email,
            passwordHash = "hashedPassword123",
            nickname = "testuser",
            createdAt = LocalDateTime.now()
        )
        
        When("DENY_NEW_LOGIN 정책에서 기존 세션이 있는 상태로 로그인을 시도하면") {
            coEvery { userRepository.findByEmail(any()) } returns Mono.just(existingUser)
            every { passwordEncoder.matches(any(), any()) } returns true
            every { duplicateLoginConfig.enabled } returns true
            every { duplicateLoginConfig.action } returns DuplicateLoginConfigAction.DENY_NEW_LOGIN
            every { duplicateLoginConfig.maxConcurrentSessions } returns 1
            coEvery { jwtSessionService.getActiveSessionCount(any()) } returns Mono.just(1L)
            
            Then("AuthenticationException이 발생해야 한다") {
                runTest {
                    val exception = shouldThrow<AuthenticationException> {
                        authService.login(loginRequest)
                    }
                    exception.errorCode shouldBe ErrorCode.MAX_SESSION_EXCEEDED
                }
            }
        }
        
        When("세션 제한 내에서 로그인을 시도하면") {
            coEvery { userRepository.findByEmail(any()) } returns Mono.just(existingUser)
            every { passwordEncoder.matches(any(), any()) } returns true
            every { duplicateLoginConfig.enabled } returns true
            every { duplicateLoginConfig.action } returns DuplicateLoginConfigAction.DENY_NEW_LOGIN
            every { duplicateLoginConfig.maxConcurrentSessions } returns 2
            coEvery { jwtSessionService.getActiveSessionCount(any()) } returns Mono.just(1L)
            coEvery { jwtSessionService.registerSession(any(), any(), any(), any(), any(), any()) } returns Mono.just(true)
            
            every { jwtTokenProvider.generateAccessToken(any(), any(), any()) } returns "access.token.here"
            every { jwtTokenProvider.generateRefreshToken(any(), any(), any()) } returns "refresh.token.here"
            every { jwtProperties.expiration } returns 3600000L
            every { jwtProperties.refreshExpiration } returns 86400000L
            
            Then("로그인이 성공해야 한다") {
                runTest {
                    val response = authService.login(loginRequest)
                    
                    response.accessToken.shouldNotBeEmpty()
                    response.refreshToken.shouldNotBeEmpty()
                    response.user.id shouldBe existingUser.id
                    
                    coVerify { jwtSessionService.getActiveSessionCount(existingUser.id!!) }
                    coVerify { jwtSessionService.registerSession(any(), existingUser.id!!, any(), any(), any(), any()) }
                }
            }
        }
    }
    
    Given("토큰 갱신 처리 시") {
        val refreshTokenRequest = com.simplechat.dto.auth.RefreshTokenRequest(
            refreshToken = "valid.refresh.token"
        )
        
        val userId = 1L
        
        When("유효한 리프레시 토큰이 주어지면") {
            every { jwtTokenProvider.refreshAccessToken(any()) } returns "new.access.token"
            every { jwtProperties.expiration } returns 3600000L
            
            Then("새로운 토큰이 발급되어야 한다") {
                runTest {
                    val response = authService.refreshToken(refreshTokenRequest)
                    
                    response.accessToken shouldBe "new.access.token"
                    response.expiresIn shouldBe 3600000L
                }
            }
        }
        
        When("유효하지 않은 리프레시 토큰이 주어지면") {
            every { jwtTokenProvider.refreshAccessToken(any()) } returns null
            
            Then("JwtAuthenticationException이 발생해야 한다") {
                runTest {
                    val exception = shouldThrow<com.simplechat.domain.exception.auth.JwtAuthenticationException> {
                        authService.refreshToken(refreshTokenRequest)
                    }
                    exception.errorCode shouldBe ErrorCode.JWT_AUTHENTICATION_FAILED
                }
            }
        }
    }
})