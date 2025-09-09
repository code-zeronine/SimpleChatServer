package com.simplechat.controller

import com.simplechat.dto.UserDto
import com.simplechat.security.JwtAuthenticationHelper
import com.simplechat.service.AuthService
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

/**
 * UserController 단위 테스트 - 간소화된 코루틴 버전
 */
class UserControllerTest {

    private lateinit var authService: AuthService
    private lateinit var jwtAuthenticationHelper: JwtAuthenticationHelper
    private lateinit var userController: UserController

    @BeforeEach
    fun setUp() {
        authService = mockk()
        jwtAuthenticationHelper = mockk()
        userController = UserController(authService, jwtAuthenticationHelper)
    }

    @Test
    fun `should initialize controller successfully`() = runTest {
        // Given & When & Then
        assertNotNull(userController)
    }
}