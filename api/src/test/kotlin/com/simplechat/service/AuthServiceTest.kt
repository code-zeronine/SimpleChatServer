package com.simplechat.service

import com.simplechat.domain.repository.UserRepository
import com.simplechat.infrastructure.config.DuplicateLoginConfig
import com.simplechat.infrastructure.config.JwtProperties
import com.simplechat.infrastructure.security.jwt.JwtSessionService
import com.simplechat.infrastructure.security.jwt.JwtTokenProvider
import com.simplechat.infrastructure.session.WebSocketSessionManager
import com.simplechat.infrastructure.websocket.handler.WebSocketMessageHandler
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
    private lateinit var sessionInvalidationService: SessionInvalidationService
    private lateinit var webSocketSessionManager: WebSocketSessionManager
    private lateinit var webSocketMessageHandler: WebSocketMessageHandler
    private lateinit var duplicateLoginConfig: DuplicateLoginConfig
    private lateinit var jwtSessionService: JwtSessionService
    private lateinit var authService: AuthService

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        passwordEncoder = mockk()
        jwtTokenProvider = mockk()
        sessionInvalidationService = mockk()
        webSocketSessionManager = mockk()
        webSocketMessageHandler = mockk()
        duplicateLoginConfig = mockk()
        jwtSessionService = mockk()
        jwtProperties = JwtProperties().apply {
            expiration = 3600000L
            refreshExpiration = 604800000L
            issuer = "test-issuer"
        }
        authService = AuthService(
            userRepository, 
            passwordEncoder, 
            jwtTokenProvider, 
            jwtProperties,
            sessionInvalidationService,
            webSocketSessionManager,
            webSocketMessageHandler,
            duplicateLoginConfig,
            jwtSessionService
        )
    }

    @Test
    fun `should initialize service successfully`() = runTest {
        // Given & When & Then
        assertNotNull(authService)
    }
}