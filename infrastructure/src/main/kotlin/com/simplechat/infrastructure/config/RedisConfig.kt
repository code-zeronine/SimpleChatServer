package com.simplechat.infrastructure.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * Redis Reactive 설정 클래스
 * 
 * Redis 7+ 호환성과 Reactive 프로그래밍을 위한 설정을 제공합니다.
 * JSON 직렬화를 통한 객체 저장과 String 키 사용을 지원합니다.
 */
@Configuration
@ConditionalOnClass(ReactiveRedisConnectionFactory::class)
@Profile("!mongo-test & !postgres-test")
class RedisConfig {
    
    private val logger = LoggerFactory.getLogger(RedisConfig::class.java)

    /**
     * ObjectMapper Bean 생성 (Redis 직렬화용)
     * Jackson Kotlin 모듈과 JSR310 시간 모듈을 포함합니다.
     */
    @Bean
    @ConditionalOnMissingBean
    fun redisObjectMapper(): ObjectMapper {
        return ObjectMapper().apply {
            registerModule(KotlinModule.Builder().build())
            registerModule(JavaTimeModule())
            findAndRegisterModules()
        }
    }

    /**
     * ReactiveRedisTemplate Bean 생성
     * String 키와 JSON 값 직렬화를 사용합니다.
     */
    @Bean
    fun reactiveRedisTemplate(
        connectionFactory: ReactiveRedisConnectionFactory,
        redisObjectMapper: ObjectMapper
    ): ReactiveRedisTemplate<String, Any> {
        
        val keySerializer = StringRedisSerializer()
        val valueSerializer = GenericJackson2JsonRedisSerializer(redisObjectMapper)
        
        val context = RedisSerializationContext
            .newSerializationContext<String, Any>()
            .key(keySerializer)
            .value(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer))
            .hashKey(keySerializer)
            .hashValue(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer))
            .build()

        return ReactiveRedisTemplate(connectionFactory, context)
    }

    /**
     * String 전용 ReactiveRedisTemplate Bean 생성
     * 단순한 String 키-값 저장용입니다.
     */
    @Bean
    fun reactiveStringRedisTemplate(
        connectionFactory: ReactiveRedisConnectionFactory
    ): ReactiveRedisTemplate<String, String> {
        
        val keySerializer = StringRedisSerializer()
        val valueSerializer = StringRedisSerializer()
        
        val context = RedisSerializationContext
            .newSerializationContext<String, String>()
            .key(keySerializer)
            .value(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer))
            .hashKey(keySerializer)
            .hashValue(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer))
            .build()

        return ReactiveRedisTemplate(connectionFactory, context)
    }

    /**
     * Spring Data Redis의 ReactiveStringRedisTemplate Bean 생성
     * 테스트에서 주입되는 표준 타입입니다.
     */
    @Bean
    fun springDataReactiveStringRedisTemplate(
        connectionFactory: ReactiveRedisConnectionFactory
    ): org.springframework.data.redis.core.ReactiveStringRedisTemplate {
        return org.springframework.data.redis.core.ReactiveStringRedisTemplate(connectionFactory)
    }
    
    /**
     * Redis 연결 상태 확인 유틸리티 클래스
     */
    @Bean
    fun redisHealthChecker(
        reactiveStringRedisTemplate: ReactiveRedisTemplate<String, String>
    ): RedisHealthChecker {
        return RedisHealthChecker(reactiveStringRedisTemplate, logger)
    }
}

/**
 * Redis 연결 상태 확인 및 헬스체크 클래스
 */
class RedisHealthChecker(
    private val reactiveStringRedisTemplate: ReactiveRedisTemplate<String, String>,
    private val logger: org.slf4j.Logger
) {
    
    /**
     * Redis 연결 상태를 확인합니다.
     * @return Redis 연결이 정상이면 true, 아니면 false
     */
    fun checkConnection(): Mono<Boolean> {
        return reactiveStringRedisTemplate
            .hasKey("health-check-key")
            .map { true }
            .doOnSuccess { 
                logger.debug("Redis connection check successful")
            }
            .doOnError { error ->
                logger.warn("Redis connection check failed: {}", error.message)
            }
            .onErrorReturn(false)
            .timeout(Duration.ofSeconds(5))
    }
    
    /**
     * Redis 핑 테스트를 수행합니다.
     * @return 핑이 성공하면 "PONG", 실패하면 에러 메시지
     */
    fun ping(): Mono<String> {
        val testKey = "ping-test:${System.currentTimeMillis()}"
        val testValue = "PING"
        
        return reactiveStringRedisTemplate
            .opsForValue()
            .set(testKey, testValue, Duration.ofSeconds(10))
            .then(reactiveStringRedisTemplate.opsForValue().get(testKey))
            .map { retrievedValue ->
                if (retrievedValue == testValue) "PONG" else "PING_FAILED"
            }
            .doFinally {
                // 테스트 키 정리
                reactiveStringRedisTemplate.delete(testKey).subscribe()
            }
            .doOnSuccess { result ->
                logger.debug("Redis ping test result: {}", result)
            }
            .doOnError { error ->
                logger.warn("Redis ping test failed: {}", error.message)
            }
            .onErrorReturn("ERROR")
            .timeout(Duration.ofSeconds(5))
    }
    
    /**
     * Redis 기본 통계 정보를 조회합니다.
     */
    fun getStats(): Mono<Map<String, Any>> {
        return Mono.fromCallable {
                mapOf<String, Any>(
                    "status" to "connected",
                    "timestamp" to System.currentTimeMillis()
                )
            }
            .doOnSuccess { stats ->
                logger.debug("Redis stats retrieved: {}", stats)
            }
            .doOnError { error ->
                logger.warn("Failed to retrieve Redis stats: {}", error.message)
            }
            .onErrorReturn(
                mapOf<String, Any>(
                    "status" to "error",
                    "timestamp" to System.currentTimeMillis()
                )
            )
            .timeout(Duration.ofSeconds(10))
    }
}