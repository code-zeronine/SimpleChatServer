package com.simplechat.infrastructure.config

import io.r2dbc.spi.ConnectionFactory
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.test.StepVerifier
import kotlin.test.assertNotNull
import java.time.Duration

@SpringBootApplication
@Import(R2dbcConfig::class)
class R2dbcTestApplication

@SpringBootTest(classes = [R2dbcTestApplication::class])
@ActiveProfiles("test")
class R2dbcConfigTest {

    @Autowired
    private lateinit var connectionFactory: ConnectionFactory

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    @Autowired
    private lateinit var r2dbcEntityTemplate: R2dbcEntityTemplate

    @Autowired
    private lateinit var transactionalOperator: TransactionalOperator

    @Test
    fun `should inject R2DBC components successfully`() {
        // 모든 R2DBC 관련 컴포넌트가 정상적으로 주입되는지 확인
        assertNotNull(connectionFactory, "ConnectionFactory should be injected")
        assertNotNull(databaseClient, "DatabaseClient should be injected")
        assertNotNull(r2dbcEntityTemplate, "R2dbcEntityTemplate should be injected")
        assertNotNull(transactionalOperator, "TransactionalOperator should be injected")
    }

    @Test
    fun `should execute simple database query`() {
        // 간단한 데이터베이스 쿼리 테스트
        StepVerifier.create(
            databaseClient
                .sql("SELECT 1 as test")
                .map { row, _ -> row.get("test", Integer::class.java) }
                .one()
        )
        .expectNext(1 as Integer)
        .verifyComplete()
    }

    @Test
    fun `should test database version`() {
        // PostgreSQL 버전 확인
        StepVerifier.create(
            databaseClient
                .sql("SELECT version() as db_version")
                .map { row, _ -> row.get("db_version", String::class.java) }
                .one()
        )
        .assertNext { version ->
            assertNotNull(version, "Database version should not be null")
            assert(version?.contains("PostgreSQL") == true) { "Should be PostgreSQL database" }
        }
        .verifyComplete()
    }

    @Test
    fun `should test connection factory creation`() {
        // ConnectionFactory가 실제로 연결을 생성할 수 있는지 테스트
        StepVerifier.create(
            databaseClient
                .sql("SELECT CURRENT_TIMESTAMP as now")
                .map { row, _ -> row.get("now", java.time.LocalDateTime::class.java) }
                .one()
        )
        .assertNext { timestamp ->
            assertNotNull(timestamp, "Current timestamp should not be null")
        }
        .verifyComplete()
    }

    @Test
    fun `should handle connection errors gracefully`() {
        // 잘못된 쿼리로 에러 핸들링 테스트
        StepVerifier.create(
            databaseClient
                .sql("SELECT * FROM definitely_non_existent_table")
                .fetch()
                .all()
        )
        .expectError()
        .verify(Duration.ofSeconds(5))
    }
}