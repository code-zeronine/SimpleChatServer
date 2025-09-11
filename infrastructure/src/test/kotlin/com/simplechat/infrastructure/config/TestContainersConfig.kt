package com.simplechat.infrastructure.config

import com.mongodb.ConnectionString
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.spi.ConnectionFactory
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.PostgreSQLContainer

/**
 * 테스트 컨테이너 설정
 * 
 * Testcontainers를 사용하여 실제 데이터베이스 환경에서 통합 테스트를 수행합니다.
 */
@TestConfiguration
class TestContainersConfig {
    
    companion object {
        // PostgreSQL 컨테이너
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("test-schema.sql")
            .apply { start() }
        
        // MongoDB 컨테이너  
        val mongodb: MongoDBContainer = MongoDBContainer("mongo:7.0")
            .withExposedPorts(27017)
            .apply { start() }
    }
    
    /**
     * 테스트용 PostgreSQL R2DBC ConnectionFactory
     */
    @Bean
    @Primary
    fun testConnectionFactory(): ConnectionFactory {
        return PostgresqlConnectionFactory(
            PostgresqlConnectionConfiguration.builder()
                .host(postgres.host)
                .port(postgres.getMappedPort(5432))
                .database(postgres.databaseName)
                .username(postgres.username)
                .password(postgres.password)
                .build()
        )
    }
    
    /**
     * 테스트용 R2DBC EntityTemplate
     */
    @Bean
    @Primary
    fun testR2dbcEntityTemplate(connectionFactory: ConnectionFactory): R2dbcEntityTemplate {
        return R2dbcEntityTemplate(connectionFactory)
    }
    
    /**
     * 테스트용 MongoDB ReactiveMongoTemplate
     */
    @Bean
    @Primary
    fun testReactiveMongoTemplate(): ReactiveMongoTemplate {
        val connectionString = ConnectionString("mongodb://${mongodb.host}:${mongodb.getMappedPort(27017)}/testdb")
        val factory = SimpleReactiveMongoDatabaseFactory(connectionString)
        return ReactiveMongoTemplate(factory)
    }
}