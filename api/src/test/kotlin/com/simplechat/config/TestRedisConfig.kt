package com.simplechat.config

import com.simplechat.infrastructure.config.DuplicateLoginConfig
import com.simplechat.infrastructure.config.DuplicateLoginConfigAction
import com.simplechat.infrastructure.config.JwtProperties
import com.simplechat.infrastructure.security.jwt.JwtSessionService
import org.mockito.Mockito
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.core.ReactiveStringRedisTemplate

/**
 * 테스트 환경용 구성
 * 테스트 시 필요한 모든 빈들을 Mock으로 제공
 */
@TestConfiguration
@Profile("test") 
class TestRedisConfig {

    @Bean
    @Primary
    fun mockReactiveStringRedisTemplate(): ReactiveStringRedisTemplate {
        return Mockito.mock(ReactiveStringRedisTemplate::class.java)
    }

    @Bean
    @Primary
    fun mockJwtSessionService(): JwtSessionService {
        return Mockito.mock(JwtSessionService::class.java)
    }

    @Bean
    @Primary
    fun testDuplicateLoginConfig(): DuplicateLoginConfig {
        return DuplicateLoginConfig(
            enabled = false,
            action = DuplicateLoginConfigAction.NOTIFY_ONLY,
            maxConcurrentSessions = 1
        )
    }

    @Bean
    @Primary
    fun testJwtProperties(): JwtProperties {
        return JwtProperties().apply {
            secret = "test-secret-key-at-least-256-bits-long-for-hs256-algorithm"
            expiration = 3600000L
            refreshExpiration = 604800000L
            issuer = "test-issuer"
            prefix = "Bearer"
        }
    }
}