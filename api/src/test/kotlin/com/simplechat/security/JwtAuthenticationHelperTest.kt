package com.simplechat.security

import com.simplechat.domain.exception.JwtAuthenticationException
import com.simplechat.infrastructure.security.jwt.JwtTokenProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import reactor.test.StepVerifier
import kotlin.test.*

/**
 * JwtAuthenticationHelper 단위 테스트
 */
class JwtAuthenticationHelperTest {

    @Mock
    private lateinit var jwtTokenProvider: JwtTokenProvider

    private lateinit var jwtAuthenticationHelper: JwtAuthenticationHelper

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        jwtAuthenticationHelper = JwtAuthenticationHelper(jwtTokenProvider)
    }

    @Test
    fun `should extract token from valid Bearer header`() {
        // Given
        val authHeader = "Bearer valid_token_here"

        // When & Then
        StepVerifier.create(jwtAuthenticationHelper.extractTokenFromHeader(authHeader))
            .assertNext { token ->
                assertEquals("valid_token_here", token)
            }
            .verifyComplete()
    }

    @Test
    fun `should fail to extract token from null header`() {
        // When & Then
        StepVerifier.create(jwtAuthenticationHelper.extractTokenFromHeader(null))
            .expectError(JwtAuthenticationException::class.java)
            .verify()
    }

    @Test
    fun `should fail to extract token from invalid header format`() {
        // Given
        val authHeader = "Basic invalid_token"

        // When & Then
        StepVerifier.create(jwtAuthenticationHelper.extractTokenFromHeader(authHeader))
            .expectError(JwtAuthenticationException::class.java)
            .verify()
    }

    @Test
    fun `should fail to extract token from header without Bearer prefix`() {
        // Given
        val authHeader = "token_without_bearer"

        // When & Then
        StepVerifier.create(jwtAuthenticationHelper.extractTokenFromHeader(authHeader))
            .expectError(JwtAuthenticationException::class.java)
            .verify()
    }

    @Test
    fun `should validate valid token successfully`() {
        // Given
        val validToken = "valid_token"
        `when`(jwtTokenProvider.validateToken(validToken)).thenReturn(true)

        // When & Then
        StepVerifier.create(jwtAuthenticationHelper.validateToken(validToken))
            .verifyComplete()

        verify(jwtTokenProvider).validateToken(validToken)
    }

    @Test
    fun `should fail validation for invalid token`() {
        // Given
        val invalidToken = "invalid_token"
        `when`(jwtTokenProvider.validateToken(invalidToken)).thenReturn(false)

        // When & Then
        StepVerifier.create(jwtAuthenticationHelper.validateToken(invalidToken))
            .expectError(JwtAuthenticationException::class.java)
            .verify()

        verify(jwtTokenProvider).validateToken(invalidToken)
    }

    @Test
    fun `should extract email from token successfully`() {
        // Given
        val token = "valid_token"
        val expectedEmail = "test@example.com"
        `when`(jwtTokenProvider.getEmailFromToken(token)).thenReturn(expectedEmail)

        // When & Then
        StepVerifier.create(jwtAuthenticationHelper.getEmailFromToken(token))
            .assertNext { email ->
                assertEquals(expectedEmail, email)
            }
            .verifyComplete()

        verify(jwtTokenProvider).getEmailFromToken(token)
    }

    @Test
    fun `should handle error when extracting username from token`() {
        // Given
        val token = "invalid_token"
        `when`(jwtTokenProvider.getEmailFromToken(token))
            .thenThrow(RuntimeException("Token parsing error"))

        // When & Then
        StepVerifier.create(jwtAuthenticationHelper.getEmailFromToken(token))
            .expectError(JwtAuthenticationException::class.java)
            .verify()

        verify(jwtTokenProvider).getEmailFromToken(token)
    }
}