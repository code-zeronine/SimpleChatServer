package com.simplechat.controller

import com.simplechat.domain.exception.AuthenticationException
import com.simplechat.domain.exception.ValidationException
import com.simplechat.dto.AuthResponse
import com.simplechat.dto.LoginRequest
import com.simplechat.dto.RefreshTokenRequest
import com.simplechat.dto.RefreshTokenResponse
import com.simplechat.dto.SignUpRequest
import com.simplechat.dto.UserDto
import com.simplechat.service.AuthService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.springframework.http.HttpStatus
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * AuthController 단위 테스트
 */
class AuthControllerTest {

    @Mock
    private lateinit var authService: AuthService

    private lateinit var authController: AuthController

    private val testUserDto = UserDto(
        id = 1L,
        email = "test@example.com",
        nickname = "testuser",
        createdAt = LocalDateTime.now().toString()
    )

    private val testAuthResponse = AuthResponse(
        accessToken = "access_token",
        refreshToken = "refresh_token",
        expiresIn = 3600000L,
        user = testUserDto
    )

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        authController = AuthController(authService)
    }

    @Test
    fun `should signup successfully`() {
        // Given
        val signUpRequest = SignUpRequest(
            email = "newuser@example.com",
            password = "password123",
            nickname = "newuser"
        )
        
        `when`(authService.signUp(signUpRequest)).thenReturn(Mono.just(testAuthResponse))

        // When & Then
        StepVerifier.create(authController.signUp(signUpRequest))
            .assertNext { response ->
                assertEquals(HttpStatus.CREATED, response.statusCode)
                assertNotNull(response.body)
                assertNotNull(response.body!!.data)
                assertEquals("access_token", response.body!!.data!!.accessToken)
                assertEquals("refresh_token", response.body!!.data!!.refreshToken)
                assertEquals(testUserDto.email, response.body!!.data!!.user.email)
            }
            .verifyComplete()
    }

    @Test
    fun `should fail signup with validation error`() {
        // Given
        val signUpRequest = SignUpRequest(
            email = "existing@example.com",
            password = "password123",
            nickname = "newuser"
        )
        
        val validationException = ValidationException("이미 사용 중인 이메일입니다.", "email")
        `when`(authService.signUp(signUpRequest)).thenReturn(Mono.error(validationException))

        // When & Then
        StepVerifier.create(authController.signUp(signUpRequest))
            .expectError(ValidationException::class.java)
            .verify()
    }

    @Test
    fun `should login successfully`() {
        // Given
        val loginRequest = LoginRequest(
            email = "test@example.com",
            password = "password123"
        )
        
        `when`(authService.login(loginRequest)).thenReturn(Mono.just(testAuthResponse))

        // When & Then
        StepVerifier.create(authController.login(loginRequest))
            .assertNext { response ->
                assertEquals(HttpStatus.OK, response.statusCode)
                assertNotNull(response.body)
                assertNotNull(response.body!!.data)
                assertEquals("access_token", response.body!!.data!!.accessToken)
                assertEquals("refresh_token", response.body!!.data!!.refreshToken)
                assertEquals(testUserDto.email, response.body!!.data!!.user.email)
            }
            .verifyComplete()
    }

    @Test
    fun `should fail login with authentication error`() {
        // Given
        val loginRequest = LoginRequest(
            email = "test@example.com",
            password = "wrongpassword"
        )
        
        val authException = AuthenticationException("이메일 또는 비밀번호가 올바르지 않습니다.")
        `when`(authService.login(loginRequest)).thenReturn(Mono.error(authException))

        // When & Then
        StepVerifier.create(authController.login(loginRequest))
            .expectError(AuthenticationException::class.java)
            .verify()
    }

    @Test
    fun `should refresh token successfully`() {
        // Given
        val refreshTokenRequest = RefreshTokenRequest(refreshToken = "valid_refresh_token")
        val refreshTokenResponse = RefreshTokenResponse(
            accessToken = "new_access_token",
            expiresIn = 3600000L
        )
        
        `when`(authService.refreshToken(refreshTokenRequest)).thenReturn(Mono.just(refreshTokenResponse))

        // When & Then
        StepVerifier.create(authController.refreshToken(refreshTokenRequest))
            .assertNext { response ->
                assertEquals(HttpStatus.OK, response.statusCode)
                assertNotNull(response.body)
                assertNotNull(response.body!!.data)
                assertEquals("new_access_token", response.body!!.data!!.accessToken)
                assertEquals(3600000L, response.body!!.data!!.expiresIn)
            }
            .verifyComplete()
    }

    @Test
    fun `should check email exists`() {
        // Given
        val email = "existing@example.com"
        `when`(authService.checkEmailExists(email)).thenReturn(Mono.just(true))

        // When & Then
        StepVerifier.create(authController.checkEmailExists(email))
            .assertNext { response ->
                assertEquals(HttpStatus.OK, response.statusCode)
                assertNotNull(response.body)
                assertNotNull(response.body!!.data)
                assertEquals(true, response.body!!.data!!["exists"])
            }
            .verifyComplete()
    }

    @Test
    fun `should check email not exists`() {
        // Given
        val email = "nonexistent@example.com"
        `when`(authService.checkEmailExists(email)).thenReturn(Mono.just(false))

        // When & Then
        StepVerifier.create(authController.checkEmailExists(email))
            .assertNext { response ->
                assertEquals(HttpStatus.OK, response.statusCode)
                assertNotNull(response.body)
                assertNotNull(response.body!!.data)
                assertEquals(false, response.body!!.data!!["exists"])
            }
            .verifyComplete()
    }

    @Test
    fun `should check nickname exists`() {
        // Given
        val nickname = "existinguser"
        `when`(authService.checkNicknameExists(nickname)).thenReturn(Mono.just(true))

        // When & Then
        StepVerifier.create(authController.checkNicknameExists(nickname))
            .assertNext { response ->
                assertEquals(HttpStatus.OK, response.statusCode)
                assertNotNull(response.body)
                assertNotNull(response.body!!.data)
                assertEquals(true, response.body!!.data!!["exists"])
            }
            .verifyComplete()
    }

    @Test
    fun `should check nickname not exists`() {
        // Given
        val nickname = "newuser"
        `when`(authService.checkNicknameExists(nickname)).thenReturn(Mono.just(false))

        // When & Then
        StepVerifier.create(authController.checkNicknameExists(nickname))
            .assertNext { response ->
                assertEquals(HttpStatus.OK, response.statusCode)
                assertNotNull(response.body)
                assertNotNull(response.body!!.data)
                assertEquals(false, response.body!!.data!!["exists"])
            }
            .verifyComplete()
    }

}