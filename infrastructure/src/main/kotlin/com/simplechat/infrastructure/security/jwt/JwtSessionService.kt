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
        logger.debug("Looking up session info for token ID: {}", tokenId)
        return redisTemplate.opsForValue()
            .get("$SESSION_INFO_PREFIX$tokenId")
            .map { 
                val sessionInfo = deserializeSessionInfo(it)
                logger.debug("Found session info for token ID {}: {}", tokenId, sessionInfo != null)
                sessionInfo
            }
            .doOnNext { sessionInfo ->
                if (sessionInfo == null) {
                    logger.debug("No session info found for token ID: {}", tokenId)
                }
            }
    }

    /**
     * 토큰 세션 무효화
     */
    suspend fun invalidateSession(token: String): Mono<Boolean> {
        val tokenId = extractTokenId(token)
        
        logger.debug("Attempting to invalidate session with token ID: {}", tokenId)
        
        return getSessionInfo(token)
            .switchIfEmpty(Mono.empty())
            .flatMap { sessionInfo ->
                if (sessionInfo != null) {
                    logger.debug("Found session info for token ID: {}, user: {}", tokenId, sessionInfo.userId)
                    invalidateSessionByTokenId(tokenId, sessionInfo.userId)
                } else {
                    logger.warn("No session info found for token ID: {}, attempting direct deletion", tokenId)
                    deleteSessionByTokenIdOnly(tokenId)
                }
            }
            .switchIfEmpty(
                Mono.defer {
                    logger.warn("No session info found for token ID: {}, attempting direct deletion", tokenId)
                    deleteSessionByTokenIdOnly(tokenId)
                }
            )
            .doOnSuccess { result ->
                logger.debug("Session invalidation result for token ID {}: {}", tokenId, result)
            }
            .doOnError { error ->
                logger.error("Failed to invalidate session for token ID {}: {}", tokenId, error.message)
            }
            .onErrorReturn(false)
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

        logger.info("🔑 [REDIS_STORE] Storing session with tokenId: {} for userId: {}", tokenId, userId)
        logger.info("🔑 [REDIS_STORE] Keys will be: session_info={}, user_sessions={}", 
            "$SESSION_INFO_PREFIX$tokenId", "$USER_SESSIONS_PREFIX$userId")

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
        logger.info("🔑 [REDIS_DELETE] Deleting session with tokenId: {} for userId: {}", tokenId, userId)
        logger.info("🔑 [REDIS_DELETE] Keys to delete: session_info={}, user_sessions_member={}", 
            "$SESSION_INFO_PREFIX$tokenId", "$USER_SESSIONS_PREFIX$userId -> $tokenId")
        
        return redisTemplate.opsForValue()
            .delete("$SESSION_INFO_PREFIX$tokenId")
            .flatMap { deleted ->
                logger.info("🔑 [REDIS_DELETE] Session info deleted: {}", deleted)
                redisTemplate.opsForSet()
                    .remove("$USER_SESSIONS_PREFIX$userId", tokenId)
                    .map { removed -> 
                        // 세션 정보 삭제 또는 사용자 세트에서 제거 중 하나라도 성공하면 성공으로 간주
                        val result = deleted || removed > 0
                        logger.info("🔑 [REDIS_DELETE] Final result - info deleted: {}, removed from user set: {}, overall success: {}", 
                            deleted, removed, result)
                        result
                    }
            }
    }
    
    /**
     * 사용자 정보 없이 토큰 ID만으로 세션 삭제 (fallback)
     */
    private fun deleteSessionByTokenIdOnly(tokenId: String): Mono<Boolean> {
        logger.debug("Attempting direct session deletion for token ID: {}", tokenId)
        
        // 1. 모든 사용자 세션에서 해당 토큰 ID 제거
        return redisTemplate.scan()
            .filter { key -> key.startsWith(USER_SESSIONS_PREFIX) }
            .flatMap { userSessionKey ->
                redisTemplate.opsForSet()
                    .remove(userSessionKey, tokenId)
                    .map { removed -> removed > 0 }
            }
            .any { it }
            .flatMap { removedFromUserSet ->
                // 2. 세션 정보도 삭제
                redisTemplate.opsForValue()
                    .delete("$SESSION_INFO_PREFIX$tokenId")
                    .map { deletedInfo ->
                        val result = removedFromUserSet || deletedInfo
                        logger.debug("Direct session deletion - removed from user set: {}, deleted info: {}, final result: {}", 
                            removedFromUserSet, deletedInfo, result)
                        result
                    }
            }
    }

    /**
     * 토큰에서 고유 ID 추출 (JTI 클레임 또는 토큰 해시)
     */
    private fun extractTokenId(token: String): String {
        return try {
            // JWT의 JTI 클레임을 사용하거나, 없으면 토큰 해시 사용
            val jti = jwtTokenProvider.getJtiFromToken(token)
            if (jti != null) {
                logger.info("✅ [TOKEN_ID] Extracted JTI from token: {} (token prefix: {})", jti, token.take(20))
                jti
            } else {
                val hash = token.hashCode().toString()
                logger.info("⚠️ [TOKEN_ID] No JTI found, using token hash: {} (token prefix: {})", hash, token.take(20))
                hash
            }
        } catch (e: Exception) {
            val hash = token.hashCode().toString()
            logger.warn("❌ [TOKEN_ID] Failed to extract token ID ({}), using hash: {} (token prefix: {})", e.message, hash, token.take(20))
            hash
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