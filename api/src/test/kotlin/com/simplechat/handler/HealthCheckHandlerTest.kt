package com.simplechat.handler

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class HealthCheckHandlerTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Test
    fun `should return health status`() {
        webTestClient.get()
            .uri("/health")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP")
            .jsonPath("$.service").isEqualTo("simple-chat-server")
            .jsonPath("$.timestamp").exists()
    }

    @Test
    fun `should return pong`() {
        webTestClient.get()
            .uri("/ping")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.message").isEqualTo("pong")
            .jsonPath("$.timestamp").exists()
    }

    @Test
    fun `should return system info`() {
        webTestClient.get()
            .uri("/info")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.service").isEqualTo("simple-chat-server")
            .jsonPath("$.version").isEqualTo("0.0.1-SNAPSHOT")
            .jsonPath("$.java").exists()
            .jsonPath("$.system").exists()
            .jsonPath("$.memory").exists()
    }
}