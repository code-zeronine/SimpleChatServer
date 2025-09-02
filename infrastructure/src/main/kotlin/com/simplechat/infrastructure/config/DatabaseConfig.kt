package com.simplechat.infrastructure.config

import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoClients
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import io.r2dbc.spi.ConnectionFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory
import org.springframework.data.mongodb.config.AbstractReactiveMongoConfiguration
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper
import org.springframework.data.mongodb.core.convert.MappingMongoConverter
import org.springframework.data.mongodb.core.convert.MongoCustomConversions
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.reactive.TransactionalOperator

/**
 * 통합 데이터베이스 설정
 * 
 * MongoDB, PostgreSQL R2DBC, 인덱스 관리, 스케줄링을 통합 관리합니다.
 * - MongoDB 설정 및 템플릿
 * - PostgreSQL R2DBC 설정 및 트랜잭션
 * - MongoDB 인덱스 자동 생성
 * - 데이터베이스 관련 스케줄링 활성화
 */
@Configuration
@EnableReactiveMongoRepositories(basePackages = ["com.simplechat.infrastructure.repository"])
@EnableR2dbcRepositories(basePackages = ["com.simplechat.infrastructure.repository"])
@EnableScheduling
class DatabaseConfig : AbstractReactiveMongoConfiguration() {

    private val logger = LoggerFactory.getLogger(DatabaseConfig::class.java)

    // MongoDB 설정
    @Value("\${spring.data.mongodb.uri:mongodb://simplechat:simplechat123@localhost:27017/simplechat}")
    private lateinit var mongoUri: String

    // PostgreSQL R2DBC 설정
    @Value("\${spring.r2dbc.url}")
    private lateinit var r2dbcUrl: String

    @Value("\${spring.r2dbc.username}")
    private lateinit var r2dbcUsername: String

    @Value("\${spring.r2dbc.password}")
    private lateinit var r2dbcPassword: String

    // ===========================================
    // MongoDB 설정
    // ===========================================

    override fun getDatabaseName(): String {
        return mongoUri.substringAfterLast("/").substringBefore("?")
    }

    @Bean
    override fun reactiveMongoClient(): MongoClient {
        return MongoClients.create(mongoUri)
    }

    @Bean
    override fun reactiveMongoTemplate(
        reactiveMongoDatabaseFactory: ReactiveMongoDatabaseFactory,
        mongoConverter: MappingMongoConverter,
    ): ReactiveMongoTemplate {
        val template = ReactiveMongoTemplate(reactiveMongoClient(), databaseName)
        val converter = template.converter as MappingMongoConverter
        converter.setTypeMapper(DefaultMongoTypeMapper(null))
        return template
    }

    @Bean
    override fun customConversions(): MongoCustomConversions {
        return MongoCustomConversions(emptyList<Any>())
    }

    // ===========================================
    // PostgreSQL R2DBC 설정
    // ===========================================

    @Bean
    fun connectionFactory(): ConnectionFactory {
        val urlParts = r2dbcUrl.removePrefix("r2dbc:postgresql://").split("/")
        val hostPort = urlParts[0].split(":")
        val host = hostPort[0]
        val port = hostPort.getOrNull(1)?.toIntOrNull() ?: 5432
        val database = urlParts.getOrNull(1) ?: "simplechatserver"

        return PostgresqlConnectionFactory(
            PostgresqlConnectionConfiguration.builder()
                .host(host)
                .port(port)
                .username(r2dbcUsername)
                .password(r2dbcPassword)
                .database(database)
                .build()
        )
    }

    @Bean
    fun reactiveTransactionManager(): ReactiveTransactionManager {
        return R2dbcTransactionManager(connectionFactory())
    }

    @Bean
    fun transactionalOperator(reactiveTransactionManager: ReactiveTransactionManager): TransactionalOperator {
        return TransactionalOperator.create(reactiveTransactionManager)
    }

    @Bean
    fun r2dbcEntityTemplate(): R2dbcEntityTemplate {
        return R2dbcEntityTemplate(connectionFactory())
    }

    @Bean
    fun connectionFactoryInitializer(): ConnectionFactoryInitializer {
        val initializer = ConnectionFactoryInitializer()
        initializer.setConnectionFactory(connectionFactory())
        return initializer
    }

    // MongoDB 인덱스 관리는 별도 MongoIndexInitializer 클래스에서 처리
}