package com.simplechat.infrastructure.service

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Redis 연결 관리 및 기본 연산을 담당하는 서비스
 * 
 * 작업 6.2: ReactiveRedisTemplate 구성 및 Redis 연결 설정 완성을 위한 서비스
 * - Redis 연결 상태 관리
 * - 기본적인 Redis 연산 제공
 * - 연결 복구 및 에러 처리
 */
@Service
class RedisConnectionService(
    private val reactiveRedisTemplate: ReactiveRedisTemplate<String, Any>,
    private val reactiveStringRedisTemplate: ReactiveRedisTemplate<String, String>
) {
    
    private val logger = LoggerFactory.getLogger(RedisConnectionService::class.java)
    
    /**
     * Redis 연결 상태 확인
     */
    fun checkConnection(): Mono<Boolean> {
        return reactiveStringRedisTemplate.hasKey("health-check")
            .doOnSuccess { 
                logger.debug("✅ Redis connection check successful")
            }
            .doOnError { error ->
                logger.warn("❌ Redis connection check failed: {}", error.message)
            }
            .onErrorReturn(false)
            .timeout(Duration.ofSeconds(5))
    }
    
    /**
     * Redis PING 테스트
     */
    fun ping(): Mono<String> {
        val timestamp = System.currentTimeMillis()
        val testKey = "ping-test:$timestamp"
        val testValue = "PONG-$timestamp"
        
        return reactiveStringRedisTemplate.opsForValue()
            .set(testKey, testValue, Duration.ofSeconds(10))
            .then(reactiveStringRedisTemplate.opsForValue().get(testKey))
            .map { value ->
                if (value == testValue) "PONG" else "PING_FAILED"
            }
            .doFinally {
                // 테스트 키 정리
                reactiveStringRedisTemplate.delete(testKey).subscribe()
            }
            .doOnSuccess { result ->
                logger.debug("🏓 Redis PING result: {}", result)
            }
            .doOnError { error ->
                logger.warn("❌ Redis PING failed: {}", error.message)
            }
            .onErrorReturn("ERROR")
            .timeout(Duration.ofSeconds(5))
    }
    
    /**
     * 기본 String 값 설정 (TTL 포함)
     */
    fun setValue(key: String, value: String, ttl: Duration = Duration.ofHours(1)): Mono<Boolean> {
        require(key.isNotBlank()) { "Key cannot be blank" }
        require(value.isNotBlank()) { "Value cannot be blank" }
        
        return reactiveStringRedisTemplate.opsForValue()
            .set(key, value, ttl)
            .doOnSuccess { success ->
                if (success) {
                    logger.debug("📝 Set key '{}' with TTL: {}", key, ttl)
                } else {
                    logger.warn("❌ Failed to set key '{}'", key)
                }
            }
            .doOnError { error ->
                logger.error("❌ Error setting key '{}': {}", key, error.message, error)
            }
            .onErrorReturn(false)
    }
    
    /**
     * String 값 조회
     */
    fun getValue(key: String): Mono<String> {
        require(key.isNotBlank()) { "Key cannot be blank" }
        
        return reactiveStringRedisTemplate.opsForValue()
            .get(key)
            .doOnNext { value ->
                logger.debug("📖 Retrieved key '{}': {}", key, if (value.length > 50) "${value.take(50)}..." else value)
            }
            .doOnError { error ->
                logger.error("❌ Error getting key '{}': {}", key, error.message, error)
            }
    }
    
    /**
     * 객체 값 설정 (JSON 직렬화)
     */
    fun setObject(key: String, obj: Any, ttl: Duration = Duration.ofHours(1)): Mono<Boolean> {
        require(key.isNotBlank()) { "Key cannot be blank" }
        
        return reactiveRedisTemplate.opsForValue()
            .set(key, obj, ttl)
            .doOnSuccess { success ->
                if (success) {
                    logger.debug("📝 Set object key '{}' ({})", key, obj.javaClass.simpleName)
                } else {
                    logger.warn("❌ Failed to set object key '{}'", key)
                }
            }
            .doOnError { error ->
                logger.error("❌ Error setting object key '{}': {}", key, error.message, error)
            }
            .onErrorReturn(false)
    }
    
    /**
     * 객체 값 조회 (JSON 역직렬화)
     */
    fun getObject(key: String): Mono<Any> {
        require(key.isNotBlank()) { "Key cannot be blank" }
        
        return reactiveRedisTemplate.opsForValue()
            .get(key)
            .doOnNext { obj ->
                logger.debug("📖 Retrieved object key '{}': {}", key, obj?.javaClass?.simpleName ?: "null")
            }
            .doOnError { error ->
                logger.error("❌ Error getting object key '{}': {}", key, error.message, error)
            }
    }
    
    /**
     * 키 존재 여부 확인
     */
    fun hasKey(key: String): Mono<Boolean> {
        require(key.isNotBlank()) { "Key cannot be blank" }
        
        return reactiveStringRedisTemplate.hasKey(key)
            .doOnNext { exists ->
                logger.debug("🔍 Key '{}' exists: {}", key, exists)
            }
            .doOnError { error ->
                logger.error("❌ Error checking key '{}': {}", key, error.message, error)
            }
            .onErrorReturn(false)
    }
    
    /**
     * 키 삭제
     */
    fun deleteKey(key: String): Mono<Boolean> {
        require(key.isNotBlank()) { "Key cannot be blank" }
        
        return reactiveStringRedisTemplate.delete(key)
            .map { deletedCount -> deletedCount > 0 }
            .doOnNext { deleted ->
                logger.debug("🗑️ Key '{}' deleted: {}", key, deleted)
            }
            .doOnError { error ->
                logger.error("❌ Error deleting key '{}': {}", key, error.message, error)
            }
            .onErrorReturn(false)
    }
    
    /**
     * TTL 조회
     */
    fun getTtl(key: String): Mono<Duration> {
        require(key.isNotBlank()) { "Key cannot be blank" }
        
        return reactiveStringRedisTemplate.getExpire(key)
            .doOnNext { ttl ->
                logger.debug("⏱️ Key '{}' TTL: {}", key, ttl)
            }
            .doOnError { error ->
                logger.error("❌ Error getting TTL for key '{}': {}", key, error.message, error)
            }
            .onErrorReturn(Duration.ZERO)
    }
    
    /**
     * Redis 통계 정보 수집
     */
    fun getRedisStats(): Mono<Map<String, Any>> {
        return Mono.fromCallable {
            mapOf<String, Any>(
                "service" to "RedisConnectionService",
                "timestamp" to LocalDateTime.now().toString(),
                "connectionStatus" to "Active",
                "templateType" to "ReactiveRedisTemplate"
            )
        }
        .doOnSuccess { stats ->
            logger.debug("📊 Redis stats: {}", stats)
        }
        .doOnError { error ->
            logger.warn("❌ Failed to collect Redis stats: {}", error.message)
        }
        .onErrorReturn(
            mapOf<String, Any>(
                "error" to "Stats collection failed",
                "timestamp" to LocalDateTime.now().toString()
            )
        )
    }
}