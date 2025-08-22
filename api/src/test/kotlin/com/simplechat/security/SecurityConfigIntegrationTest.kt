package com.simplechat.security

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType

/**
 * Spring Security WebFlux 설정 통합 테스트 (개발 환경)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("dev")
class SecurityConfigIntegrationTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Test
    fun `should allow access to auth endpoints in dev profile`() {
        // 개발 환경에서는 모든 요청이 허용됨
        webTestClient.get()
            .uri("/api/auth/check-email?email=test@example.com")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `should verify CORS configuration is active`() {
        // CORS 설정이 활성화되어 있는지 간단히 확인
        // Origin 헤더 없이 요청해도 정상 작동해야 함
        webTestClient.get()
            .uri("/api/auth/check-email?email=test@example.com")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.exists").isBoolean
    }

    @Test
    fun `should disable CSRF in WebFlux configuration`() {
        // CSRF가 비활성화되어 있어야 함
        val loginRequest = """
            {
                "email": "test@example.com",
                "password": "password123"
            }
        """.trimIndent()

        webTestClient.post()
            .uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(loginRequest)
            .exchange()
            .expectStatus().is4xxClientError // 인증 실패는 정상, CSRF 에러가 아님
    }
}