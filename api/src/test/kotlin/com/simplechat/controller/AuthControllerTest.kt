package com.simplechat.controller

import com.simplechat.dto.AuthResponse
import com.simplechat.dto.SignUpRequest
import com.simplechat.dto.UserDto
import com.simplechat.service.AuthService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * AuthController 단위 테스트 - 간소화된 코루틴 버전
 */
class AuthControllerTest {

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
        authService = mockk()
        authController = AuthController(authService)
    }

    @Test
    fun `should signup successfully`() = runTest {
        // Given
        val signUpRequest = SignUpRequest(
            email = "newuser@example.com",
            password = "password123",
            nickname = "newuser"
        )
        
        coEvery { authService.signUp(signUpRequest) } returns testAuthResponse

        // When
        val response = authController.signUp(signUpRequest)

        // Then
        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNotNull(response.body)
        assertNotNull(response.body!!.data)
        assertEquals("access_token", response.body!!.data!!.accessToken)
    }
}