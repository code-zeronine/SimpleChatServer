package com.simplechat

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class SimpleChatServerApplicationTests {

    @Test
    fun contextLoads() {
        // Test that the application context loads successfully
    }
}