package com.simplechat.service

import com.simplechat.domain.entity.User
import com.simplechat.domain.exception.AuthenticationException
import com.simplechat.domain.exception.JwtAuthenticationException
import com.simplechat.domain.exception.ValidationException
import com.simplechat.domain.repository.UserRepository
import com.simplechat.dto.LoginRequest
import com.simplechat.dto.RefreshTokenRequest
import com.simplechat.dto.SignUpRequest
import com.simplechat.infrastructure.config.JwtProperties
import com.simplechat.infrastructure.security.jwt.JwtTokenProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.springframework.security.crypto.password.PasswordEncoder
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.LocalDateTime
import kotlin.test.assertTrue

/**
 * AuthService 단위 테스트
 */
class AuthServiceTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @Mock
    private lateinit var passwordEncoder: PasswordEncoder

    @Mock
    private lateinit var jwtTokenProvider: JwtTokenProvider

    private lateinit var jwtProperties: JwtProperties
    private lateinit var authService: AuthService

    private val testUser = User(
        id = 1L,
        email = "test@example.com",
        passwordHash = "encoded_password",
        nickname = "testuser",
        createdAt = LocalDateTime.now()
    )

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        
        jwtProperties = JwtProperties().apply {
            secret = "testSecretKeyForAuthServiceUnitTestingPurpose123456789"
            expiration = 3600000L
            refreshExpiration = 86400000L
            issuer = "test-simple-chat-server"
            header = "Authorization"
            prefix = "Bearer"
        }
        
        authService = AuthService(userRepository, passwordEncoder, jwtTokenProvider, jwtProperties)
    }

    @Test
    fun `should check email exists correctly`() {
        // Given
        val email = "existing@example.com"
        `when`(userRepository.existsByEmail(email)).thenReturn(Mono.just(true))

        // When & Then
        StepVerifier.create(authService.checkEmailExists(email))
            .assertNext { exists ->
                assertTrue(exists)
            }
            .verifyComplete()
    }

    @Test
    fun `should check nickname exists correctly`() {
        // Given
        val nickname = "existinguser"
        `when`(userRepository.existsByNickname(nickname)).thenReturn(Mono.just(true))

        // When & Then
        StepVerifier.create(authService.checkNicknameExists(nickname))
            .assertNext { exists ->
                assertTrue(exists)
            }
            .verifyComplete()
    }

    @Test
    fun `should fail signup with duplicate email`() {
        // Given
        val signUpRequest = SignUpRequest(
            email = "existing@example.com",
            password = "password123",
            nickname = "newuser"
        )
        
        `when`(userRepository.existsByEmail(signUpRequest.email)).thenReturn(Mono.just(true))

        // When & Then
        StepVerifier.create(authService.signUp(signUpRequest))
            .expectError(ValidationException::class.java)
            .verify()
    }

    @Test
    fun `should fail signup with duplicate nickname`() {
        // Given
        val signUpRequest = SignUpRequest(
            email = "newuser@example.com",
            password = "password123",
            nickname = "existinguser"
        )
        
        `when`(userRepository.existsByEmail(signUpRequest.email)).thenReturn(Mono.just(false))
        `when`(userRepository.existsByNickname(signUpRequest.nickname)).thenReturn(Mono.just(true))

        // When & Then
        StepVerifier.create(authService.signUp(signUpRequest))
            .expectError(ValidationException::class.java)
            .verify()
    }

    @Test
    fun `should fail login with non-existent email`() {
        // Given
        val loginRequest = LoginRequest(
            email = "nonexistent@example.com",
            password = "password123"
        )
        
        `when`(userRepository.findByEmail(loginRequest.email)).thenReturn(Mono.empty())

        // When & Then
        StepVerifier.create(authService.login(loginRequest))
            .expectError(AuthenticationException::class.java)
            .verify()
    }

    @Test
    fun `should fail login with wrong password`() {
        // Given
        val loginRequest = LoginRequest(
            email = "test@example.com",
            password = "wrongpassword"
        )
        
        `when`(userRepository.findByEmail(loginRequest.email)).thenReturn(Mono.just(testUser))
        `when`(passwordEncoder.matches(loginRequest.password, testUser.passwordHash)).thenReturn(false)

        // When & Then
        StepVerifier.create(authService.login(loginRequest))
            .expectError(AuthenticationException::class.java)
            .verify()
    }

    @Test
    fun `should fail refresh token with invalid token`() {
        // Given
        val refreshTokenRequest = RefreshTokenRequest(refreshToken = "invalid_token")
        
        `when`(jwtTokenProvider.validateToken(refreshTokenRequest.refreshToken)).thenReturn(false)

        // When & Then
        StepVerifier.create(authService.refreshToken(refreshTokenRequest))
            .expectError(JwtAuthenticationException::class.java)
            .verify()
    }

    @Test
    fun `should fail refresh token with access token instead of refresh token`() {
        // Given
        val refreshTokenRequest = RefreshTokenRequest(refreshToken = "access_token")
        
        `when`(jwtTokenProvider.validateToken(refreshTokenRequest.refreshToken)).thenReturn(true)
        `when`(jwtTokenProvider.isRefreshToken(refreshTokenRequest.refreshToken)).thenReturn(false)

        // When & Then
        StepVerifier.create(authService.refreshToken(refreshTokenRequest))
            .expectError(JwtAuthenticationException::class.java)
            .verify()
    }

    @Test
    fun `should fail validate user with invalid token`() {
        // Given
        val token = "invalid_token"
        
        `when`(jwtTokenProvider.validateToken(token)).thenReturn(false)

        // When & Then
        StepVerifier.create(authService.validateUser(token))
            .expectError(JwtAuthenticationException::class.java)
            .verify()
    }
}