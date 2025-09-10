package com.simplechat.service

import com.simplechat.infrastructure.session.WebSocketSessionManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono
import kotlin.test.assertEquals

@DisplayName("SessionInvalidationService 테스트")
class SessionInvalidationServiceTest {

    private lateinit var webSocketSessionManager: WebSocketSessionManager
    private lateinit var sessionInvalidationService: SessionInvalidationService
    private lateinit var mockSession1: WebSocketSession
    private lateinit var mockSession2: WebSocketSession

    @BeforeEach
    fun setUp() {
        webSocketSessionManager = mockk()
        sessionInvalidationService = SessionInvalidationService(webSocketSessionManager)
        mockSession1 = mockk()
        mockSession2 = mockk()
    }

    @Test
    @DisplayName("사용자의 모든 세션을 성공적으로 무효화")
    fun `should invalidate all user sessions successfully`() = runBlocking {
        // Given
        val userId = 1L
        val sessions = listOf(mockSession1, mockSession2)
        
        every { webSocketSessionManager.getSessionsByUserId(userId) } returns sessions
        every { mockSession1.id } returns "session1"
        every { mockSession2.id } returns "session2"
        every { mockSession1.close() } returns Mono.empty()
        every { mockSession2.close() } returns Mono.empty()
        every { webSocketSessionManager.removeSession("session1") } returns Unit
        every { webSocketSessionManager.removeSession("session2") } returns Unit

        // When
        val result = sessionInvalidationService.invalidateAllUserSessions(userId)

        // Then
        assertEquals(2, result)
        verify { mockSession1.close() }
        verify { mockSession2.close() }
        verify { webSocketSessionManager.removeSession("session1") }
        verify { webSocketSessionManager.removeSession("session2") }
    }

    @Test
    @DisplayName("특정 세션을 제외하고 다른 세션들을 무효화")
    fun `should invalidate other user sessions excluding specific session`() = runBlocking {
        // Given
        val userId = 1L
        val excludeSessionId = "session1"
        val sessions = listOf(mockSession1, mockSession2)
        
        every { webSocketSessionManager.getSessionsByUserId(userId) } returns sessions
        every { mockSession1.id } returns "session1"
        every { mockSession2.id } returns "session2"
        every { mockSession2.close() } returns Mono.empty()
        every { webSocketSessionManager.removeSession("session2") } returns Unit

        // When
        val result = sessionInvalidationService.invalidateOtherUserSessions(userId, excludeSessionId)

        // Then
        assertEquals(1, result)
        verify(exactly = 0) { mockSession1.close() }
        verify { mockSession2.close() }
        verify(exactly = 0) { webSocketSessionManager.removeSession("session1") }
        verify { webSocketSessionManager.removeSession("session2") }
    }

    @Test
    @DisplayName("활성 세션이 없는 경우 0을 반환")
    fun `should return 0 when no active sessions`() = runBlocking {
        // Given
        val userId = 1L
        every { webSocketSessionManager.getSessionsByUserId(userId) } returns emptyList()

        // When
        val result = sessionInvalidationService.invalidateAllUserSessions(userId)

        // Then
        assertEquals(0, result)
    }

    @Test
    @DisplayName("사용자의 활성 세션 수를 정확히 반환")
    fun `should return correct active session count`() {
        // Given
        val userId = 1L
        every { webSocketSessionManager.getUserActiveSessionCount(userId) } returns 3

        // When
        val result = sessionInvalidationService.getUserActiveSessionCount(userId)

        // Then
        assertEquals(3, result)
        verify { webSocketSessionManager.getUserActiveSessionCount(userId) }
    }
}