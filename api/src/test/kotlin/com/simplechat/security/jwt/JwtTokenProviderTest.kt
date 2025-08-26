package com.simplechat.security.jwt

import com.simplechat.config.JwtProperties
import com.simplechat.exception.JwtAuthenticationException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

/**
 * JwtTokenProvider 단위 테스트
 */
class JwtTokenProviderTest {

    private lateinit var jwtTokenProvider: JwtTokenProvider
    private lateinit var jwtProperties: JwtProperties

    @BeforeEach
    fun setUp() {
        jwtProperties = JwtProperties().apply {
            secret = "testSecretKeyForJwtTokenProviderUnitTestingPurpose123456789"
            expiration = 3600000L // 1 hour
            refreshExpiration = 86400000L // 24 hours
            issuer = "test-simple-chat-server"
            header = "Authorization"
            prefix = "Bearer"
        }
        jwtTokenProvider = JwtTokenProvider(jwtProperties)
    }

    @Test
    fun `should generate valid access token`() {
        // Given
        val username = "testuser@example.com"
        val roles = listOf("USER", "ADMIN")

        // When
        val token = jwtTokenProvider.generateAccessToken(username, roles)

        // Then
        assertNotNull(token)
        assertTrue(token.isNotEmpty())
        assertTrue(jwtTokenProvider.validateToken(token))
        assertEquals(username, jwtTokenProvider.getEmailFromToken(token))
        assertEquals(roles, jwtTokenProvider.getRolesFromToken(token))
        assertTrue(jwtTokenProvider.isAccessToken(token))
        assertFalse(jwtTokenProvider.isRefreshToken(token))
    }

    @Test
    fun `should generate valid refresh token`() {
        // Given
        val username = "testuser@example.com"

        // When
        val refreshToken = jwtTokenProvider.generateRefreshToken(username)

        // Then
        assertNotNull(refreshToken)
        assertTrue(refreshToken.isNotEmpty())
        assertTrue(jwtTokenProvider.validateToken(refreshToken))
        assertEquals(username, jwtTokenProvider.getEmailFromToken(refreshToken))
        assertTrue(jwtTokenProvider.isRefreshToken(refreshToken))
        assertFalse(jwtTokenProvider.isAccessToken(refreshToken))
    }

    @Test
    fun `should extract username from valid token`() {
        // Given
        val username = "testuser@example.com"
        val token = jwtTokenProvider.generateAccessToken(username)

        // When
        val extractedUsername = jwtTokenProvider.getEmailFromToken(token)

        // Then
        assertEquals(username, extractedUsername)
    }

    @Test
    fun `should extract roles from valid token`() {
        // Given
        val username = "testuser@example.com"
        val roles = listOf("USER", "ADMIN")
        val token = jwtTokenProvider.generateAccessToken(username, roles)

        // When
        val extractedRoles = jwtTokenProvider.getRolesFromToken(token)

        // Then
        assertEquals(roles, extractedRoles)
    }

    @Test
    fun `should return empty roles list for token without roles`() {
        // Given
        val username = "testuser@example.com"
        val token = jwtTokenProvider.generateAccessToken(username)

        // When
        val extractedRoles = jwtTokenProvider.getRolesFromToken(token)

        // Then
        assertTrue(extractedRoles.isEmpty())
    }

    @Test
    fun `should validate correct token`() {
        // Given
        val username = "testuser@example.com"
        val token = jwtTokenProvider.generateAccessToken(username)

        // When
        val isValid = jwtTokenProvider.validateToken(token)

        // Then
        assertTrue(isValid)
    }

    @Test
    fun `should not validate malformed token`() {
        // Given
        val malformedToken = "invalid.token.format"

        // When
        val isValid = jwtTokenProvider.validateToken(malformedToken)

        // Then
        assertFalse(isValid)
    }

    @Test
    fun `should not validate empty token`() {
        // Given
        val emptyToken = ""

        // When
        val isValid = jwtTokenProvider.validateToken(emptyToken)

        // Then
        assertFalse(isValid)
    }

    @Test
    fun `should identify access token correctly`() {
        // Given
        val username = "testuser@example.com"
        val accessToken = jwtTokenProvider.generateAccessToken(username)
        val refreshToken = jwtTokenProvider.generateRefreshToken(username)

        // When & Then
        assertTrue(jwtTokenProvider.isAccessToken(accessToken))
        assertFalse(jwtTokenProvider.isAccessToken(refreshToken))
    }

    @Test
    fun `should identify refresh token correctly`() {
        // Given
        val username = "testuser@example.com"
        val accessToken = jwtTokenProvider.generateAccessToken(username)
        val refreshToken = jwtTokenProvider.generateRefreshToken(username)

        // When & Then
        assertTrue(jwtTokenProvider.isRefreshToken(refreshToken))
        assertFalse(jwtTokenProvider.isRefreshToken(accessToken))
    }

    @Test
    fun `should check token expiration correctly`() {
        // Given
        val username = "testuser@example.com"
        val token = jwtTokenProvider.generateAccessToken(username)

        // When
        val isExpired = jwtTokenProvider.isTokenExpired(token)

        // Then
        assertFalse(isExpired) // Should not be expired immediately
    }

    @Test
    fun `should get expiration date from token`() {
        // Given
        val username = "testuser@example.com"
        val token = jwtTokenProvider.generateAccessToken(username)

        // When
        val expirationDate = jwtTokenProvider.getExpirationFromToken(token)

        // Then
        assertNotNull(expirationDate)
        assertTrue(expirationDate.time > System.currentTimeMillis())
    }

    @Test
    fun `should resolve bearer token correctly`() {
        // Given
        val token = "sample.jwt.token"
        val bearerToken = "Bearer $token"

        // When
        val resolvedToken = jwtTokenProvider.resolveToken(bearerToken)

        // Then
        assertEquals(token, resolvedToken)
    }

    @Test
    fun `should return null for invalid bearer token format`() {
        // Given
        val invalidBearerToken = "InvalidFormat sample.jwt.token"

        // When
        val resolvedToken = jwtTokenProvider.resolveToken(invalidBearerToken)

        // Then
        assertNull(resolvedToken)
    }

    @Test
    fun `should return null for null bearer token`() {
        // When
        val resolvedToken = jwtTokenProvider.resolveToken(null)

        // Then
        assertNull(resolvedToken)
    }

    @Test
    fun `should refresh access token with valid refresh token`() {
        // Given
        val username = "testuser@example.com"
        val refreshToken = jwtTokenProvider.generateRefreshToken(username)

        // When
        val newAccessToken = jwtTokenProvider.refreshAccessToken(refreshToken)

        // Then
        assertNotNull(newAccessToken)
        assertTrue(jwtTokenProvider.validateToken(newAccessToken!!))
        assertEquals(username, jwtTokenProvider.getEmailFromToken(newAccessToken))
        assertTrue(jwtTokenProvider.isAccessToken(newAccessToken))
    }

    @Test
    fun `should not refresh access token with access token`() {
        // Given
        val username = "testuser@example.com"
        val accessToken = jwtTokenProvider.generateAccessToken(username)

        // When
        val result = jwtTokenProvider.refreshAccessToken(accessToken)

        // Then
        assertNull(result)
    }

    @Test
    fun `should not refresh access token with invalid token`() {
        // Given
        val invalidToken = "invalid.token.format"

        // When
        val result = jwtTokenProvider.refreshAccessToken(invalidToken)

        // Then
        assertNull(result)
    }

    @Test
    fun `should throw exception when extracting username from invalid token`() {
        // Given
        val invalidToken = "invalid.token.format"

        // When & Then
        assertThrows<JwtAuthenticationException> {
            jwtTokenProvider.getEmailFromToken(invalidToken)
        }
    }

    @Test
    fun `should handle token with different issuer`() {
        // Given
        val differentIssuerProperties = JwtProperties().apply {
            secret = jwtProperties.secret
            issuer = "different-issuer"
        }
        val differentProvider = JwtTokenProvider(differentIssuerProperties)
        val username = "testuser@example.com"
        val tokenWithDifferentIssuer = differentProvider.generateAccessToken(username)

        // When & Then
        assertFalse(jwtTokenProvider.validateToken(tokenWithDifferentIssuer))
    }
}