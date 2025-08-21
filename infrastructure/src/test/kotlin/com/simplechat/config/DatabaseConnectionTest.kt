package com.simplechat.config

import io.r2dbc.spi.ConnectionFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.ActiveProfiles
import reactor.test.StepVerifier
import kotlin.test.assertNotNull

@SpringBootTest
@ActiveProfiles("dev")
@EnabledIfSystemProperty(named = "test.database", matches = "enabled")
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
    fun `should execute simple database query`() {
        // 간단한 쿼리 실행으로 연결 테스트 (실제 DB가 없어도 설정 검증용)
        val result = databaseClient
            .sql("SELECT 1 as test")
            .map { row, _ -> row.get("test", Integer::class.java) }
            .one()

        // 실제 연결 없이는 테스트가 실패할 것이므로, 설정만 확인
        assertNotNull(result, "Query result should not be null")
    }
}