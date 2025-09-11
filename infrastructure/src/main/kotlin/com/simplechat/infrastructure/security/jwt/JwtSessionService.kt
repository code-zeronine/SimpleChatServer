package com.simplechat.infrastructure.security.jwt

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant

/**
 * JWT 기반 활성 세션 추적 서비스
 * Redis를 사용하여 활성 JWT 토큰을 관리하고 중복 로그인을 제어합니다.
 */
@Service
class JwtSessionService(
    @Qualifier("jwtSessionRedisTemplate") private val redisTemplate: ReactiveStringRedisTemplate,
    private val jwtTokenProvider: JwtTokenProvider
) {

    private val logger = LoggerFactory.getLogger(JwtSessionService::class.java)

    companion object {
        private const val USER_SESSIONS_PREFIX = "jwt:sessions:user:"
        private const val TOKEN_SESSIONS_PREFIX = "jwt:sessions:token:"
        private const val SESSION_INFO_PREFIX = "jwt:info:"
    }

    /**
     * JWT 토큰 세션 정보
     */
    data class JwtSessionInfo(
        val userId: Long,
        val email: String,
        val tokenId: String,
        val issuedAt: Instant,
        val expiresAt: Instant,
        val ipAddress: String? = null,
        val userAgent: String? = null,
        val lastAccessTime: Instant = Instant.now()
    )

    /**
     * 활성 JWT 세션 등록
     */
    suspend fun registerSession(
        token: String,
        userId: Long,
        email: String,
        expirationDuration: Duration,
        ipAddress: String? = null,
        userAgent: String? = null
    ): Mono<Boolean> {
        val tokenId = extractTokenId(token)
        val now = Instant.now()
        val expiresAt = now.plus(expirationDuration)
        
        val sessionInfo = JwtSessionInfo(
            userId = userId,
            email = email,
            tokenId = tokenId,
            issuedAt = now,
            expiresAt = expiresAt,
            ipAddress = ipAddress,
            userAgent = userAgent
        )

        logger.debug("Registering JWT session for user: {} with token ID: {}", userId, tokenId)

        return registerSessionInRedis(sessionInfo, expirationDuration)
    }

    /**
     * 사용자의 활성 세션 수 조회
     */
    suspend fun getActiveSessionCount(userId: Long): Mono<Long> {
        return redisTemplate.opsForSet()
            .members("$USER_SESSIONS_PREFIX$userId")
            .count()
    }

    /**
     * 사용자의 모든 활성 세션 정보 조회
     */
    suspend fun getActiveSessionsInfo(userId: Long): Flux<JwtSessionInfo> {
        return redisTemplate.opsForSet()
            .members("$USER_SESSIONS_PREFIX$userId")
            .flatMap { tokenId ->
                redisTemplate.opsForValue()
                    .get("$SESSION_INFO_PREFIX$tokenId")
                    .map { deserializeSessionInfo(it) }
            }
            .filter { it != null }
            .cast(JwtSessionInfo::class.java)
    }

    /**
     * 특정 토큰의 세션 정보 조회
     */
    suspend fun getSessionInfo(token: String): Mono<JwtSessionInfo?> {
        val tokenId = extractTokenId(token)
        return redisTemplate.opsForValue()
            .get("$SESSION_INFO_PREFIX$tokenId")
            .map { deserializeSessionInfo(it) }
    }

    /**
     * 토큰 세션 무효화
     */
    suspend fun invalidateSession(token: String): Mono<Boolean> {
        val tokenId = extractTokenId(token)
        
        return getSessionInfo(token)
            .flatMap { sessionInfo ->
                if (sessionInfo != null) {
                    invalidateSessionByTokenId(tokenId, sessionInfo.userId)
                } else {
                    Mono.just(false)
                }
            }
    }

    /**
     * 사용자의 모든 세션 무효화 (현재 토큰 제외)
     */
    suspend fun invalidateAllOtherSessions(userId: Long, currentToken: String): Mono<Int> {
        val currentTokenId = extractTokenId(currentToken)
        
        return redisTemplate.opsForSet()
            .members("$USER_SESSIONS_PREFIX$userId")
            .filter { tokenId -> tokenId != currentTokenId }
            .flatMap { tokenId -> invalidateSessionByTokenId(tokenId, userId) }
            .count()
            .map { it.toInt() }
    }

    /**
     * 사용자의 모든 세션 무효화
     */
    suspend fun invalidateAllSessions(userId: Long): Mono<Int> {
        return redisTemplate.opsForSet()
            .members("$USER_SESSIONS_PREFIX$userId")
            .flatMap { tokenId -> invalidateSessionByTokenId(tokenId, userId) }
            .count()
            .map { it.toInt() }
    }

    /**
     * 세션 마지막 접근 시간 업데이트
     */
    suspend fun updateLastAccessTime(token: String): Mono<Boolean> {
        val tokenId = extractTokenId(token)
        
        return getSessionInfo(token)
            .flatMap { sessionInfo ->
                if (sessionInfo != null) {
                    val updatedInfo = sessionInfo.copy(lastAccessTime = Instant.now())
                    val serialized = serializeSessionInfo(updatedInfo)
                    val ttl = Duration.between(Instant.now(), sessionInfo.expiresAt)
                    
                    redisTemplate.opsForValue()
                        .set("$SESSION_INFO_PREFIX$tokenId", serialized, ttl)
                } else {
                    Mono.just(false)
                }
            }
    }

    /**
     * 만료된 세션들 정리
     */
    suspend fun cleanupExpiredSessions(): Mono<Int> {
        val now = Instant.now()
        
        return redisTemplate.scan()
            .filter { key -> key.startsWith(SESSION_INFO_PREFIX) }
            .flatMap { key ->
                redisTemplate.opsForValue().get(key)
                    .map { value -> key to deserializeSessionInfo(value) }
            }
            .filter { (_, sessionInfo) -> 
                sessionInfo != null && sessionInfo.expiresAt.isBefore(now)
            }
            .flatMap { (key, sessionInfo) ->
                if (sessionInfo != null) {
                    val tokenId = key.removePrefix(SESSION_INFO_PREFIX)
                    invalidateSessionByTokenId(tokenId, sessionInfo.userId)
                } else {
                    Mono.just(false)
                }
            }
            .count()
            .map { it.toInt() }
    }

    /**
     * Redis에 세션 정보 저장
     */
    private fun registerSessionInRedis(sessionInfo: JwtSessionInfo, ttl: Duration): Mono<Boolean> {
        val tokenId = sessionInfo.tokenId
        val userId = sessionInfo.userId
        val serialized = serializeSessionInfo(sessionInfo)

        return redisTemplate.opsForValue()
            .set("$SESSION_INFO_PREFIX$tokenId", serialized, ttl)
            .flatMap {
                redisTemplate.opsForSet()
                    .add("$USER_SESSIONS_PREFIX$userId", tokenId)
            }
            .flatMap {
                redisTemplate.expire("$USER_SESSIONS_PREFIX$userId", ttl)
            }
            .map { it }
    }

    /**
     * 토큰 ID로 세션 무효화
     */
    private fun invalidateSessionByTokenId(tokenId: String, userId: Long): Mono<Boolean> {
        return redisTemplate.opsForValue()
            .delete("$SESSION_INFO_PREFIX$tokenId")
            .flatMap { deleted ->
                redisTemplate.opsForSet()
                    .remove("$USER_SESSIONS_PREFIX$userId", tokenId)
                    .map { removed -> deleted && removed > 0 }
            }
    }

    /**
     * 토큰에서 고유 ID 추출 (JTI 클레임 또는 토큰 해시)
     */
    private fun extractTokenId(token: String): String {
        return try {
            // JWT의 JTI 클레임을 사용하거나, 없으면 토큰 해시 사용
            jwtTokenProvider.getJtiFromToken(token) ?: token.hashCode().toString()
        } catch (e: Exception) {
            logger.warn("Failed to extract token ID, using hash: {}", e.message)
            token.hashCode().toString()
        }
    }

    /**
     * 세션 정보 직렬화
     */
    private fun serializeSessionInfo(sessionInfo: JwtSessionInfo): String {
        return "${sessionInfo.userId}|${sessionInfo.email}|${sessionInfo.tokenId}|" +
                "${sessionInfo.issuedAt.epochSecond}|${sessionInfo.expiresAt.epochSecond}|" +
                "${sessionInfo.ipAddress ?: ""}|${sessionInfo.userAgent ?: ""}|" +
                "${sessionInfo.lastAccessTime.epochSecond}"
    }

    /**
     * 세션 정보 역직렬화
     */
    private fun deserializeSessionInfo(serialized: String): JwtSessionInfo? {
        return try {
            val parts = serialized.split("|")
            if (parts.size >= 8) {
                JwtSessionInfo(
                    userId = parts[0].toLong(),
                    email = parts[1],
                    tokenId = parts[2],
                    issuedAt = Instant.ofEpochSecond(parts[3].toLong()),
                    expiresAt = Instant.ofEpochSecond(parts[4].toLong()),
                    ipAddress = parts[5].ifEmpty { null },
                    userAgent = parts[6].ifEmpty { null },
                    lastAccessTime = Instant.ofEpochSecond(parts[7].toLong())
                )
            } else null
        } catch (e: Exception) {
            logger.warn("Failed to deserialize session info: {}", e.message)
            null
        }
    }
}