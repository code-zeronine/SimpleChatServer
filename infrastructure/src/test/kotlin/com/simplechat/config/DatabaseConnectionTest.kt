package com.simplechat.config

import io.r2dbc.spi.ConnectionFactory
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.ActiveProfiles
import reactor.test.StepVerifier
import kotlin.test.assertNotNull
import java.time.Duration

@SpringBootApplication
class TestApplication

@SpringBootTest(classes = [TestApplication::class])
@ActiveProfiles("test")
class DatabaseConnectionTest {

    @Autowired
    private lateinit var connectionFactory: ConnectionFactory

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    @Test
    fun `should inject database components successfully`() {
        // Connection Factory와 DatabaseClient가 정상적으로 주입되는지 확인
        assertNotNull(connectionFactory, "ConnectionFactory should be injected")
        assertNotNull(databaseClient, "DatabaseClient should be injected")
    }

    @Test
    fun `should execute simple database query using StepVerifier`() {
        // StepVerifier를 사용한 Reactive 스트림 테스트
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
    fun `should create and query test table`() {
        // 테스트용 테이블 생성 및 데이터 삽입/조회 테스트
        val createTableSql = """
            CREATE TABLE IF NOT EXISTS dev_test_table (
                id SERIAL PRIMARY KEY,
                name VARCHAR(100) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """.trimIndent()

        val insertSql = "INSERT INTO dev_test_table (name) VALUES ('Dev Test Name')"
        val selectSql = "SELECT name FROM dev_test_table WHERE name = 'Dev Test Name' LIMIT 1"
        val cleanupSql = "DROP TABLE IF EXISTS dev_test_table"

        StepVerifier.create(
            databaseClient.sql(createTableSql).then()
                .then(databaseClient.sql(insertSql).then())
                .then(
                    databaseClient.sql(selectSql)
                        .map { row, _ -> row.get("name", String::class.java) }
                        .one()
                )
                .doFinally { databaseClient.sql(cleanupSql).then().subscribe() }
        )
        .expectNext("Dev Test Name")
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

    @Test
    fun `should test database version and connection`() {
        // PostgreSQL 버전 확인 및 연결 테스트
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
}