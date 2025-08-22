package com.simplechat.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebFluxSecurity
class SecurityConfig {

    @Bean
    @Profile("dev")
    fun developmentSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            .cors { cors -> cors.configurationSource(corsConfigurationSource()) }
            .csrf { csrf -> csrf.disable() }
            .authorizeExchange { exchanges ->
                exchanges
                    .pathMatchers("/api/health", "/api/ping", "/api/info").permitAll()
                    .pathMatchers("/actuator/**").permitAll()
                    .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .anyExchange().permitAll() // Development: Allow all requests
            }
            .build()
    }

    @Bean
    @Profile("prod")
    fun productionSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            .cors { cors -> cors.configurationSource(corsConfigurationSource()) }
            .csrf { csrf -> csrf.disable() }
            .authorizeExchange { exchanges ->
                exchanges
                    .pathMatchers("/api/health", "/api/ping").permitAll()
                    .pathMatchers("/actuator/health").permitAll()
                    .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .pathMatchers("/api/auth/**").permitAll()
                    .anyExchange().authenticated() // Production: Require authentication
            }
            .build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOriginPatterns = listOf("*")
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
            allowedHeaders = listOf("*")
            allowCredentials = true
            maxAge = 3600L
        }

        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }

    /**
     * 비밀번호 암호화를 위한 BCrypt 인코더
     */
    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }
}