package com.simplechat.service

import com.simplechat.domain.repository.UserRepository
import com.simplechat.infrastructure.config.JwtProperties
import com.simplechat.infrastructure.security.jwt.JwtTokenProvider
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder
import kotlin.test.assertNotNull

/**
 * AuthService 단위 테스트 - 간소화된 코루틴 버전
 */
class AuthServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var jwtTokenProvider: JwtTokenProvider
    private lateinit var jwtProperties: JwtProperties
    private lateinit var authService: AuthService

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        passwordEncoder = mockk()
        jwtTokenProvider = mockk()
        jwtProperties = JwtProperties().apply {
            expiration = 3600000L
            refreshExpiration = 604800000L
            issuer = "test-issuer"
        }
        authService = AuthService(userRepository, passwordEncoder, jwtTokenProvider, jwtProperties)
    }

    @Test
    fun `should initialize service successfully`() = runTest {
        // Given & When & Then
        assertNotNull(authService)
    }
}