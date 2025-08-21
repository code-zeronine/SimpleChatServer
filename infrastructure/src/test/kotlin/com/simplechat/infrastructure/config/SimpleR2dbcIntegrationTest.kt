package com.simplechat.infrastructure.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.ActiveProfiles
import reactor.test.StepVerifier
import kotlin.test.assertNotNull

@SpringBootApplication(scanBasePackages = ["com.simplechat.infrastructure"])
class SimpleTestApplication

@SpringBootTest(
    classes = [SimpleTestApplication::class],
    properties = [
        "spring.r2dbc.url=r2dbc:postgresql://localhost:5432/simplechatserver",
        "spring.r2dbc.username=simplechat",
        "spring.r2dbc.password=simplechat123",
        "spring.sql.init.mode=never"
    ]
)
@ActiveProfiles("test")
class SimpleR2dbcIntegrationTest {

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    @Test
    fun `should have database client injected`() {
        assertNotNull(databaseClient, "DatabaseClient should be injected")
    }

    @Test
    fun `should execute simple query`() {
        StepVerifier.create(
            databaseClient
                .sql("SELECT 1 as test_value")
                .map { row, _ -> row.get("test_value", Integer::class.java) }
                .one()
        )
        .expectNext(1 as Integer)
        .verifyComplete()
    }

    @Test
    fun `should check database version`() {
        StepVerifier.create(
            databaseClient
                .sql("SELECT version() as db_version")
                .map { row, _ -> row.get("db_version", String::class.java) }
                .one()
        )
        .assertNext { version ->
            assertNotNull(version, "Database version should not be null")
            assert(version.contains("PostgreSQL")) { "Should be PostgreSQL database: $version" }
        }
        .verifyComplete()
    }
}