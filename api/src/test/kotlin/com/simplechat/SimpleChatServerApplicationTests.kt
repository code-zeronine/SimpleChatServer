package com.simplechat

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class SimpleChatServerApplicationTests {

    companion object {
        @Container
        val mongoDbContainer: GenericContainer<*> = GenericContainer(DockerImageName.parse("mongo:latest"))
            .withExposedPorts(27017)

        @Container
        val postgreSqlContainer: PostgreSQLContainer<*> = PostgreSQLContainer(DockerImageName.parse("postgres:latest"))
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass")

        @Container
        val redisContainer: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:latest"))
            .withExposedPorts(6379)

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.mongodb.uri") { "mongodb://localhost:${mongoDbContainer.getMappedPort(27017)}/testdb" }
            registry.add("spring.r2dbc.url") { "r2dbc:postgresql://localhost:${postgreSqlContainer.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)}/${postgreSqlContainer.databaseName}" }
            registry.add("spring.r2dbc.username") { postgreSqlContainer.username }
            registry.add("spring.r2dbc.password") { postgreSqlContainer.password }
            registry.add("spring.data.redis.host") { redisContainer.host }
            registry.add("spring.data.redis.port") { redisContainer.getMappedPort(6379).toString() }
        }
    }

    @Test
    fun contextLoads() {
        // Test that the application context loads successfully
    }
}