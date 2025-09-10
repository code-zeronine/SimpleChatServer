package com.simplechat.controller

import com.simplechat.security.JwtAuthenticationHelper
import com.simplechat.service.AuthService
import com.simplechat.service.SessionInvalidationService
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

@DisplayName("SessionManagementController 테스트")
class SessionManagementControllerTest {

    private lateinit var authService: AuthService
    private lateinit var sessionInvalidationService: SessionInvalidationService
    private lateinit var jwtAuthenticationHelper: JwtAuthenticationHelper
    private lateinit var controller: SessionManagementController

    @BeforeEach
    fun setUp() {
        authService = mockk()
        sessionInvalidationService = mockk()
        jwtAuthenticationHelper = mockk()
        controller = SessionManagementController(authService, sessionInvalidationService, jwtAuthenticationHelper)
    }

    @Test
    @DisplayName("컨트롤러 초기화 테스트")
    fun `should initialize controller successfully`() = runTest {
        // Given & When & Then
        assertTrue(controller != null)
    }

}