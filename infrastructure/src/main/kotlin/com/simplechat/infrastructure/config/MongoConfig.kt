package com.simplechat.infrastructure.config

import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoClients
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

@Configuration
@EnableReactiveMongoRepositories(basePackages = ["com.simplechat.infrastructure.repository"])
class MongoConfig : AbstractReactiveMongoConfiguration() {

    @Value("\${spring.data.mongodb.uri:mongodb://simplechat:simplechat123@localhost:27017/simplechat}")
    private lateinit var mongoUri: String

    override fun getDatabaseName(): String {
        // URI에서 데이터베이스 이름 추출
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
}