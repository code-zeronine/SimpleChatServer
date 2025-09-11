package com.simplechat.infrastructure.repository

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType

/**
 * 테스트용 Spring Boot 애플리케이션 클래스
 * 
 * Infrastructure 모듈의 통합 테스트를 위한 최소한의 Spring Context 설정
 * DatabaseConfig는 제외하고 TestContainersConfig만 사용
 */
@SpringBootApplication(exclude = [
    R2dbcAutoConfiguration::class,
    MongoAutoConfiguration::class,
    MongoDataAutoConfiguration::class
])
@ComponentScan(
    basePackages = ["com.simplechat.infrastructure.repository"],
    includeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [com.simplechat.infrastructure.repository.UserRepositoryImpl::class]
        )
    ],
    useDefaultFilters = false
)
class TestApplication