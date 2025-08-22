package com.simplechat.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.ServerAuthenticationEntryPoint
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets

@Configuration
@EnableWebFluxSecurity
class SecurityConfig {

    @Bean
    @Profile("dev")
    fun developmentSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            .cors { cors -> cors.configurationSource(corsConfigurationSource()) }
            .csrf { csrf -> csrf.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .exceptionHandling { exceptions ->
                exceptions
                    .authenticationEntryPoint(authenticationEntryPoint())
                    .accessDeniedHandler(accessDeniedHandler())
            }
            .authorizeExchange { exchanges ->
                exchanges
                    .pathMatchers("/api/health", "/api/ping", "/api/info").permitAll()
                    .pathMatchers("/actuator/**").permitAll()
                    .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .pathMatchers("/api/auth/**").permitAll()
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
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .exceptionHandling { exceptions ->
                exceptions
                    .authenticationEntryPoint(authenticationEntryPoint())
                    .accessDeniedHandler(accessDeniedHandler())
            }
            .authorizeExchange { exchanges ->
                exchanges
                    .pathMatchers("/api/health", "/api/ping").permitAll()
                    .pathMatchers("/actuator/health").permitAll()
                    .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .pathMatchers("/api/auth/signup", "/api/auth/login", "/api/auth/check-email", "/api/auth/check-nickname").permitAll()
                    .pathMatchers("/api/auth/refresh").authenticated() // 토큰 갱신은 인증 필요
                    .pathMatchers("/api/users/**").authenticated() // 사용자 관련 API는 인증 필요
                    .anyExchange().authenticated() // Production: Require authentication for all other endpoints
            }
            .build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            // WebFlux 환경에서 더 구체적인 CORS 설정
            allowedOriginPatterns = listOf(
                "http://localhost:*",
                "https://localhost:*",
                "http://127.0.0.1:*"
            )
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
            allowedHeaders = listOf(
                "Authorization",
                "Content-Type", 
                "Accept",
                "Origin",
                "Cache-Control",
                "X-Requested-With"
            )
            exposedHeaders = listOf("Authorization") // JWT 토큰을 클라이언트에서 읽을 수 있도록
            allowCredentials = true
            maxAge = 3600L // 1시간 캐시
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

    /**
     * WebFlux 환경에서 인증 실패 시 반응형 응답 처리
     */
    @Bean
    fun authenticationEntryPoint(): ServerAuthenticationEntryPoint {
        return ServerAuthenticationEntryPoint { exchange, _ ->
            val response = exchange.response
            response.statusCode = HttpStatus.UNAUTHORIZED
            response.headers.add("Content-Type", MediaType.APPLICATION_JSON_VALUE)

            val errorBody = """{"error":"Unauthorized","message":"Authentication required","status":401}"""
            val buffer = response.bufferFactory().wrap(errorBody.toByteArray(StandardCharsets.UTF_8))
            
            response.writeWith(Mono.just(buffer))
        }
    }

    /**
     * WebFlux 환경에서 접근 거부 시 반응형 응답 처리
     */
    @Bean
    fun accessDeniedHandler(): ServerAccessDeniedHandler {
        return ServerAccessDeniedHandler { exchange, _ ->
            val response = exchange.response
            response.statusCode = HttpStatus.FORBIDDEN
            response.headers.add("Content-Type", MediaType.APPLICATION_JSON_VALUE)

            val errorBody = """{"error":"Forbidden","message":"Access denied","status":403}"""
            val buffer = response.bufferFactory().wrap(errorBody.toByteArray(StandardCharsets.UTF_8))
            
            response.writeWith(Mono.just(buffer))
        }
    }
}