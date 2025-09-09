package com.simplechat.security

import com.simplechat.domain.exception.JwtAuthenticationException
import com.simplechat.infrastructure.security.jwt.JwtTokenProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * JwtAuthenticationHelper 단위 테스트 (Coroutine 방식)
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

        // When
        val result = jwtAuthenticationHelper.extractTokenFromHeader(authHeader)

        // Then
        assertEquals("valid_token_here", result)
    }

    @Test
    fun `should return null for null header`() {
        // When
        val result = jwtAuthenticationHelper.extractTokenFromHeader(null)

        // Then
        assertNull(result)
    }

    @Test
    fun `should return null for invalid header format`() {
        // Given
        val authHeader = "Basic invalid_token"

        // When
        val result = jwtAuthenticationHelper.extractTokenFromHeader(authHeader)

        // Then
        assertNull(result)
    }

    @Test
    fun `should return null for header without Bearer prefix`() {
        // Given
        val authHeader = "token_without_bearer"

        // When
        val result = jwtAuthenticationHelper.extractTokenFromHeader(authHeader)

        // Then
        assertNull(result)
    }

    @Test
    fun `should validate valid token successfully`() {
        // Given
        val validToken = "valid_token"
        `when`(jwtTokenProvider.validateToken(validToken)).thenReturn(true)

        // When
        val result = jwtAuthenticationHelper.validateToken(validToken)

        // Then
        assertTrue(result)
        verify(jwtTokenProvider).validateToken(validToken)
    }

    @Test
    fun `should return false for invalid token`() {
        // Given
        val invalidToken = "invalid_token"
        `when`(jwtTokenProvider.validateToken(invalidToken)).thenReturn(false)

        // When
        val result = jwtAuthenticationHelper.validateToken(invalidToken)

        // Then
        assertFalse(result)
        verify(jwtTokenProvider).validateToken(invalidToken)
    }

    @Test
    fun `should extract email from token successfully`() {
        // Given
        val token = "valid_token"
        val expectedEmail = "test@example.com"
        `when`(jwtTokenProvider.getEmailFromToken(token)).thenReturn(expectedEmail)

        // When
        val result = jwtAuthenticationHelper.getEmailFromToken(token)

        // Then
        assertEquals(expectedEmail, result)
        verify(jwtTokenProvider).getEmailFromToken(token)
    }

    @Test
    fun `should handle error when extracting email from token`() {
        // Given
        val token = "invalid_token"
        `when`(jwtTokenProvider.getEmailFromToken(token))
            .thenThrow(RuntimeException("Token parsing error"))

        // When & Then
        assertThrows<JwtAuthenticationException> {
            jwtAuthenticationHelper.getEmailFromToken(token)
        }

        verify(jwtTokenProvider).getEmailFromToken(token)
    }
}