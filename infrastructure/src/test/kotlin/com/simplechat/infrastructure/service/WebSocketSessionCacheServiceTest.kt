package com.simplechat.infrastructure.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.mockito.Mockito.lenient
import org.springframework.data.redis.core.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

@ExtendWith(MockitoExtension::class)
class WebSocketSessionCacheServiceTest {

    @Mock
    private lateinit var reactiveRedisTemplate: ReactiveRedisTemplate<String, Any>
    
    @Mock
    private lateinit var valueOps: ReactiveValueOperations<String, Any>
    
    @Mock
    private lateinit var setOps: ReactiveSetOperations<String, Any>
    
    @Mock
    private lateinit var hashOps: ReactiveHashOperations<String, String, Any>
    
    private lateinit var objectMapper: ObjectMapper
    private lateinit var sessionCacheService: WebSocketSessionCacheService
    
    @BeforeEach
    fun setUp() {
        objectMapper = ObjectMapper().apply {
            registerModule(JavaTimeModule())
            registerModule(KotlinModule.Builder().build())
        }
        
        lenient().whenever(reactiveRedisTemplate.opsForValue()).thenReturn(valueOps)
        lenient().whenever(reactiveRedisTemplate.opsForSet()).thenReturn(setOps)
        lenient().whenever(reactiveRedisTemplate.opsForHash<String, Any>()).thenReturn(hashOps)
        
        sessionCacheService = WebSocketSessionCacheService(reactiveRedisTemplate, objectMapper)
    }
    
    @Test
    fun `cacheSessionInfo should successfully cache session information`() {
        // Given
        val sessionInfo = createTestSessionInfo()
        val sessionJson = objectMapper.writeValueAsString(sessionInfo)
        val onlineStatusJson = sessionInfo.toOnlineStatus(objectMapper)
        
        whenever(valueOps.set(any<String>(), eq(sessionJson), any<Duration>()))
            .thenReturn(Mono.just(true))
        whenever(valueOps.set(any<String>(), eq(onlineStatusJson), any<Duration>()))
            .thenReturn(Mono.just(true))
        whenever(setOps.add(any<String>(), eq(sessionInfo.sessionId)))
            .thenReturn(Mono.just(1L))
        whenever(reactiveRedisTemplate.expire(any<String>(), any<Duration>()))
            .thenReturn(Mono.just(true))
        whenever(hashOps.put(any<String>(), eq("userId"), eq(sessionInfo.userId)))
            .thenReturn(Mono.just(true))
        
        // When & Then
        StepVerifier.create(sessionCacheService.cacheSessionInfo(sessionInfo))
            .expectNext(true)
            .verifyComplete()
    }
    
    @Test
    fun `getSessionInfo should retrieve session from Redis when not in local cache`() {
        // Given
        val sessionInfo = createTestSessionInfo()
        val sessionJson = objectMapper.writeValueAsString(sessionInfo)
        
        whenever(valueOps.get("websocket:session:${sessionInfo.sessionId}"))
            .thenReturn(Mono.just(sessionJson))
        whenever(reactiveRedisTemplate.expire(any<String>(), any<Duration>()))
            .thenReturn(Mono.just(true))
        
        // When & Then
        StepVerifier.create(sessionCacheService.getSessionInfo(sessionInfo.sessionId))
            .expectNext(sessionInfo)
            .verifyComplete()
    }
    
    @Test
    fun `getSessionInfo should return empty when session not found`() {
        // Given
        val sessionId = "non-existent-session"
        
        whenever(valueOps.get("websocket:session:$sessionId"))
            .thenReturn(Mono.empty())
        
        // When & Then
        StepVerifier.create(sessionCacheService.getSessionInfo(sessionId))
            .verifyComplete()
    }
    
    @Test
    fun `getUserSessions should return all active sessions for user`() {
        // Given
        val userId = 123L
        val sessionId1 = "session1"
        val sessionId2 = "session2"
        val sessionInfo1 = createTestSessionInfo(sessionId1, userId)
        val sessionInfo2 = createTestSessionInfo(sessionId2, userId)
        
        whenever(setOps.members("user:sessions:$userId"))
            .thenReturn(Flux.just(sessionId1, sessionId2))
        
        // Mock getSessionInfo calls
        whenever(valueOps.get("websocket:session:$sessionId1"))
            .thenReturn(Mono.just(objectMapper.writeValueAsString(sessionInfo1)))
        whenever(valueOps.get("websocket:session:$sessionId2"))
            .thenReturn(Mono.just(objectMapper.writeValueAsString(sessionInfo2)))
        whenever(reactiveRedisTemplate.expire(any<String>(), any<Duration>()))
            .thenReturn(Mono.just(true))
        
        // When & Then
        StepVerifier.create(sessionCacheService.getUserSessions(userId))
            .expectNextCount(2)
            .verifyComplete()
    }
    
    @Test
    fun `updateSessionHeartbeat should update session activity`() {
        // Given
        val sessionInfo = createTestSessionInfo()
        val sessionJson = objectMapper.writeValueAsString(sessionInfo)
        
        whenever(valueOps.get("websocket:session:${sessionInfo.sessionId}"))
            .thenReturn(Mono.just(sessionJson))
        whenever(reactiveRedisTemplate.expire(any<String>(), any<Duration>()))
            .thenReturn(Mono.just(true))
        
        // Mock cacheSessionInfo for updated session
        whenever(valueOps.set(any<String>(), any<String>(), any<Duration>()))
            .thenReturn(Mono.just(true))
        whenever(setOps.add(any<String>(), any<String>()))
            .thenReturn(Mono.just(1L))
        whenever(hashOps.put(any<String>(), any<String>(), any()))
            .thenReturn(Mono.just(true))
        
        // When & Then
        StepVerifier.create(sessionCacheService.updateSessionHeartbeat(sessionInfo.sessionId))
            .expectNext(true)
            .verifyComplete()
    }
    
    @Test
    fun `cleanupExpiredSessions should return cleanup count`() {
        // When & Then
        StepVerifier.create(sessionCacheService.cleanupExpiredSessions())
            .expectNextMatches { count -> count >= 0 }
            .verifyComplete()
    }
    
    @Test
    fun `getCacheStatistics should return current cache statistics`() {
        // When & Then
        StepVerifier.create(sessionCacheService.getCacheStatistics())
            .expectNextMatches { stats ->
                stats.localCacheSize >= 0 && 
                stats.timestamp != null
            }
            .verifyComplete()
    }
    
    @Test
    fun `WebSocketSessionInfo should correctly detect expired sessions`() {
        // Given
        val expiredTime = Instant.now().minus(25, ChronoUnit.HOURS) // Past the 24-hour TTL
        val expiredSession = WebSocketSessionInfo(
            sessionId = "expired-session",
            userId = 123L,
            remoteAddress = "127.0.0.1",
            userAgent = "Test Agent",
            connectedAt = expiredTime,
            lastActivityAt = expiredTime
        )
        
        val activeTime = Instant.now().minus(1, ChronoUnit.HOURS) // Within the 24-hour TTL
        val activeSession = expiredSession.copy(
            sessionId = "active-session",
            lastActivityAt = activeTime
        )
        
        // When & Then
        assert(expiredSession.isExpired())
        assert(!activeSession.isExpired())
    }
    
    @Test
    fun `toOnlineStatus should create valid JSON string`() {
        // Given
        val sessionInfo = createTestSessionInfo()
        
        // When
        val onlineStatusJson = sessionInfo.toOnlineStatus(objectMapper)
        
        // Then
        val onlineStatus = objectMapper.readValue(onlineStatusJson, UserOnlineStatus::class.java)
        assert(onlineStatus.userId == sessionInfo.userId)
        assert(onlineStatus.isOnline)
        assert(onlineStatus.lastSeenAt == sessionInfo.lastActivityAt)
    }
    
    private fun createTestSessionInfo(
        sessionId: String = "test-session-123",
        userId: Long = 123L
    ) = WebSocketSessionInfo(
        sessionId = sessionId,
        userId = userId,
        remoteAddress = "127.0.0.1:8080",
        userAgent = "Mozilla/5.0 Test Browser",
        connectedAt = Instant.now().minus(1, ChronoUnit.HOURS),
        lastActivityAt = Instant.now().minus(5, ChronoUnit.MINUTES),
        heartbeatCount = 12,
        attributes = mapOf(
            "clientIp" to "127.0.0.1",
            "origin" to "http://localhost:3000"
        )
    )
}