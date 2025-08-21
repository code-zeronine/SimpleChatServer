package com.simplechat.schema

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.ActiveProfiles
import reactor.test.StepVerifier
import kotlin.test.assertTrue

@SpringBootApplication
class SchemaTestApplication

@SpringBootTest(classes = [SchemaTestApplication::class])
@ActiveProfiles("test")
class DatabaseSchemaTest {

    @Autowired
    private lateinit var databaseClient: DatabaseClient

    @Test
    fun `should create users table with correct schema`() {
        // Given & When: schema.sql이 자동으로 실행됨
        
        // Then: 테이블 구조 확인
        StepVerifier.create(
            databaseClient
                .sql("""
                    SELECT column_name, data_type, is_nullable, column_default
                    FROM information_schema.columns 
                    WHERE table_name = 'users' 
                    ORDER BY ordinal_position
                """)
                .fetch()
                .all()
        )
        .expectNextCount(5) // id, email, password_hash, nickname, created_at
        .verifyComplete()
    }

    @Test
    fun `should have correct primary key constraint`() {
        StepVerifier.create(
            databaseClient
                .sql("""
                    SELECT constraint_name, constraint_type
                    FROM information_schema.table_constraints 
                    WHERE table_name = 'users' AND constraint_type = 'PRIMARY KEY'
                """)
                .fetch()
                .all()
        )
        .expectNextCount(1)
        .verifyComplete()
    }

    @Test
    fun `should have unique constraints on email and nickname`() {
        StepVerifier.create(
            databaseClient
                .sql("""
                    SELECT constraint_name, constraint_type
                    FROM information_schema.table_constraints 
                    WHERE table_name = 'users' AND constraint_type = 'UNIQUE'
                """)
                .fetch()
                .all()
        )
        .expectNextCount(2) // email과 nickname에 대한 UNIQUE 제약조건
        .verifyComplete()
    }

    @Test
    fun `should have required indexes`() {
        StepVerifier.create(
            databaseClient
                .sql("""
                    SELECT indexname 
                    FROM pg_indexes 
                    WHERE tablename = 'users' AND indexname LIKE 'idx_users_%'
                """)
                .fetch()
                .all()
        )
        .expectNextCount(3) // idx_users_email, idx_users_nickname, idx_users_created_at
        .verifyComplete()
    }

    @Test
    fun `should load initial test data`() {
        // Given & When: data.sql이 자동으로 실행됨
        
        // Then: 테스트 데이터 확인
        StepVerifier.create(
            databaseClient
                .sql("SELECT COUNT(*) as count FROM users")
                .map { row, _ -> row.get("count", Long::class.java) }
                .one()
        )
        .expectNext(5L) // 5개의 테스트 사용자
        .verifyComplete()
    }

    @Test
    fun `should have admin user in test data`() {
        StepVerifier.create(
            databaseClient
                .sql("SELECT email, nickname FROM users WHERE email = 'admin@simplechat.com'")
                .map { row, _ -> 
                    mapOf(
                        "email" to row.get("email", String::class.java),
                        "nickname" to row.get("nickname", String::class.java)
                    )
                }
                .one()
        )
        .assertNext { user ->
            assertTrue(user["email"] == "admin@simplechat.com")
            assertTrue(user["nickname"] == "Admin")
        }
        .verifyComplete()
    }

    @Test
    fun `should enforce email format constraint`() {
        // 잘못된 이메일 형식으로 삽입 시도
        StepVerifier.create(
            databaseClient
                .sql("""
                    INSERT INTO users (email, password_hash, nickname) 
                    VALUES ('invalid-email', 'hash123', 'testuser')
                """)
                .then()
        )
        .expectError() // CHECK 제약조건에 의해 오류 발생 예상
        .verify()
    }

    @Test
    fun `should enforce nickname length constraint`() {
        // 너무 짧은 닉네임으로 삽입 시도
        StepVerifier.create(
            databaseClient
                .sql("""
                    INSERT INTO users (email, password_hash, nickname) 
                    VALUES ('test@example.com', 'hash123', 'a')
                """)
                .then()
        )
        .expectError() // CHECK 제약조건에 의해 오류 발생 예상
        .verify()
    }

    @Test
    fun `should enforce unique email constraint`() {
        // 중복 이메일로 삽입 시도
        StepVerifier.create(
            databaseClient
                .sql("""
                    INSERT INTO users (email, password_hash, nickname) 
                    VALUES ('admin@simplechat.com', 'hash123', 'AnotherAdmin')
                """)
                .then()
        )
        .expectError() // UNIQUE 제약조건에 의해 오류 발생 예상
        .verify()
    }

    @Test
    fun `should enforce unique nickname constraint`() {
        // 중복 닉네임으로 삽입 시도
        StepVerifier.create(
            databaseClient
                .sql("""
                    INSERT INTO users (email, password_hash, nickname) 
                    VALUES ('another@example.com', 'hash123', 'Admin')
                """)
                .then()
        )
        .expectError() // UNIQUE 제약조건에 의해 오류 발생 예상
        .verify()
    }
}