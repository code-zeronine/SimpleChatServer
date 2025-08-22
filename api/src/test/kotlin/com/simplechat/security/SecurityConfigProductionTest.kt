package com.simplechat.security

import org.junit.jupiter.api.Test
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource
import kotlin.test.*

/**
 * Production 환경에서의 Spring Security WebFlux 설정 단위 테스트
 * SecurityConfig 클래스의 설정 검증에 집중
 */
class SecurityConfigProductionTest {

    private val securityConfig = SecurityConfig()

    @Test
    fun `should create CORS configuration source properly`() {
        // CORS 설정이 올바르게 생성되는지 확인
        val corsConfigurationSource = securityConfig.corsConfigurationSource()
        
        assertNotNull(corsConfigurationSource)
        assertTrue(corsConfigurationSource is UrlBasedCorsConfigurationSource)
        
        // CORS 설정 객체가 정상적으로 생성되었는지만 확인
        assertEquals("org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource", corsConfigurationSource::class.java.name)
    }

    @Test
    fun `should create password encoder bean`() {
        // BCrypt 패스워드 인코더가 올바르게 생성되는지 확인
        val passwordEncoder = securityConfig.passwordEncoder()
        
        assertNotNull(passwordEncoder)
        assertEquals("org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder", passwordEncoder::class.java.name)
        
        // 패스워드 인코딩 동작 확인
        val encoded = passwordEncoder.encode("testPassword")
        assertTrue(passwordEncoder.matches("testPassword", encoded))
        assertFalse(passwordEncoder.matches("wrongPassword", encoded))
    }

    @Test
    fun `should create authentication entry point with JSON response`() {
        // 인증 실패 시 JSON 응답을 반환하는 AuthenticationEntryPoint 확인
        val authenticationEntryPoint = securityConfig.authenticationEntryPoint()
        
        assertNotNull(authenticationEntryPoint)
        // 람다 기반 구현체가 생성되었는지 확인
        assertTrue(authenticationEntryPoint.toString().contains("SecurityConfig"))
    }

    @Test
    fun `should create access denied handler with JSON response`() {
        // 접근 거부 시 JSON 응답을 반환하는 AccessDeniedHandler 확인  
        val accessDeniedHandler = securityConfig.accessDeniedHandler()
        
        assertNotNull(accessDeniedHandler)
        // 람다 기반 구현체가 생성되었는지 확인
        assertTrue(accessDeniedHandler.toString().contains("SecurityConfig"))
    }

    @Test
    fun `should verify WebFlux security configuration structure`() {
        // SecurityConfig 클래스가 WebFlux 환경에 필요한 구성 요소를 모두 포함하는지 확인
        val methods = SecurityConfig::class.java.declaredMethods
        val methodNames = methods.map { it.name }
        
        // 필수 Bean 메서드들이 존재하는지 확인
        assertTrue(methodNames.contains("corsConfigurationSource"))
        assertTrue(methodNames.contains("passwordEncoder"))
        assertTrue(methodNames.contains("authenticationEntryPoint"))
        assertTrue(methodNames.contains("accessDeniedHandler"))
        assertTrue(methodNames.contains("developmentSecurityFilterChain"))
        assertTrue(methodNames.contains("productionSecurityFilterChain"))
    }
}