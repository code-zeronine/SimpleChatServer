package com.simplechat.controller

import com.simplechat.dto.UserDto
import com.simplechat.security.JwtAuthenticationHelper
import com.simplechat.service.AuthService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.springframework.http.HttpStatus
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * UserController 단위 테스트
 */
class UserControllerTest {

    @Mock
    private lateinit var authService: AuthService

    @Mock
    private lateinit var jwtAuthenticationHelper: JwtAuthenticationHelper

    private lateinit var userController: UserController

    private val testUserDto = UserDto(
        id = 1L,
        email = "test@example.com",
        nickname = "testuser",
        createdAt = "2023-01-01T00:00:00"
    )

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        userController = UserController(authService, jwtAuthenticationHelper)
    }

    @Test
    fun `should get current user successfully`() {
        // Given
        val authHeader = "Bearer valid_token"
        val token = "valid_token"
        
        `when`(jwtAuthenticationHelper.extractTokenFromHeader(authHeader)).thenReturn(Mono.just(token))
        `when`(jwtAuthenticationHelper.validateToken(token)).thenReturn(Mono.empty())
        `when`(authService.validateUser(token)).thenReturn(Mono.just(testUserDto))

        // When & Then
        StepVerifier.create(userController.getCurrentUser(authHeader))
            .assertNext { response ->
                assertEquals(HttpStatus.OK, response.statusCode)
                assertNotNull(response.body)
                assertNotNull(response.body!!.data)
                assertEquals(testUserDto.email, response.body!!.data!!.email)
                assertEquals(testUserDto.nickname, response.body!!.data!!.nickname)
            }
            .verifyComplete()

        verify(jwtAuthenticationHelper).extractTokenFromHeader(authHeader)
        verify(jwtAuthenticationHelper).validateToken(token)
        verify(authService).validateUser(token)
    }

    @Test
    fun `should update profile successfully`() {
        // Given
        val authHeader = "Bearer valid_token"
        val token = "valid_token"
        val email = "test@example.com"
        val newNickname = "newNickname"
        val updateRequest = mapOf("nickname" to newNickname)
        val updatedUserDto = testUserDto.copy(nickname = newNickname)

        `when`(jwtAuthenticationHelper.extractTokenFromHeader(authHeader)).thenReturn(Mono.just(token))
        `when`(jwtAuthenticationHelper.validateToken(token)).thenReturn(Mono.empty())
        `when`(jwtAuthenticationHelper.getEmailFromToken(token)).thenReturn(Mono.just(email))
        `when`(authService.updateNickname(email, newNickname)).thenReturn(Mono.just(updatedUserDto))

        // When & Then
        StepVerifier.create(userController.updateProfile(authHeader, updateRequest))
            .assertNext { response ->
                assertEquals(HttpStatus.OK, response.statusCode)
                assertNotNull(response.body)
                assertNotNull(response.body!!.data)
                assertEquals(updatedUserDto, response.body!!.data)
            }
            .verifyComplete()

        verify(jwtAuthenticationHelper).extractTokenFromHeader(authHeader)
        verify(jwtAuthenticationHelper).validateToken(token)
        verify(jwtAuthenticationHelper).getEmailFromToken(token)
        verify(authService).updateNickname(email, newNickname)
    }
}