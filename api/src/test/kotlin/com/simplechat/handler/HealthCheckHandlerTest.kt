package com.simplechat.handler

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody

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
            .expectBody<Map<String, Any>>()
            .consumeWith { response ->
                val body = response.responseBody!!
                assert(body["status"] == "UP")
                assert(body["service"] == "simple-chat-server")
                assert(body.containsKey("timestamp"))
            }
    }

    @Test
    fun `should return pong`() {
        webTestClient.get()
            .uri("/ping")
            .exchange()
            .expectStatus().isOk
            .expectBody<Map<String, Any>>()
            .consumeWith { response ->
                val body = response.responseBody!!
                assert(body["message"] == "pong")
                assert(body.containsKey("timestamp"))
            }
    }

    @Test
    fun `should return system info`() {
        webTestClient.get()
            .uri("/info")
            .exchange()
            .expectStatus().isOk
            .expectBody<Map<String, Any>>()
            .consumeWith { response ->
                val body = response.responseBody!!
                assert(body["service"] == "simple-chat-server")
                assert(body["version"] == "0.0.1-SNAPSHOT")
                assert(body.containsKey("java"))
                assert(body.containsKey("system"))
                assert(body.containsKey("memory"))
            }
    }
}