package com.simplechat.config

import io.r2dbc.spi.ConnectionFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator

@Configuration
@EnableR2dbcRepositories(basePackages = ["com.simplechat.repository"])
class DatabaseConfig {

    @Bean
    fun initializer(connectionFactory: ConnectionFactory): ConnectionFactoryInitializer {
        val initializer = ConnectionFactoryInitializer()
        initializer.setConnectionFactory(connectionFactory)
        
        // 초기 스키마 설정이 필요할 경우 주석 해제
        // initializer.setDatabasePopulator(
        //     ResourceDatabasePopulator(ClassPathResource("schema.sql"))
        // )
        
        return initializer
    }
}