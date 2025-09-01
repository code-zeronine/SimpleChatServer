package com.simplechat.security

import com.simplechat.infrastructure.security.jwt.JwtTokenProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * WebSocket 세션의 JWT 토큰 지속 검증을 담당하는 컴포넌트
 */
@Component
class WebSocketTokenValidator(
    private val jwtTokenProvider: JwtTokenProvider
) {
    
    private val logger = LoggerFactory.getLogger(WebSocketTokenValidator::class.java)
    
    // 세션별 토큰 저장소
    private val sessionTokens = ConcurrentHashMap<String, String>()
    
    /**
     * 세션에 토큰 등록
     */
    fun registerSession(sessionId: String, token: String) {
        sessionTokens[sessionId] = token
        logger.debug("Token registered for session: {}", sessionId)
    }
    
    /**
     * 세션 토큰 제거
     */
    fun unregisterSession(sessionId: String) {
        val removed = sessionTokens.remove(sessionId)
        if (removed != null) {
            logger.debug("Token unregistered for session: {}", sessionId)
        }
    }
    
    /**
     * 세션의 토큰 유효성 검증
     */
    fun validateSessionToken(sessionId: String): Mono<Boolean> {
        return Mono.fromCallable {
            val token = sessionTokens[sessionId]
            if (token == null) {
                logger.warn("No token found for session: {}", sessionId)
                return@fromCallable false
            }
            
            try {
                val isValid = jwtTokenProvider.validateToken(token)
                val isExpired = jwtTokenProvider.isTokenExpired(token)
                
                if (!isValid || isExpired) {
                    logger.warn("Invalid or expired token for session: {} (valid: {}, expired: {})", 
                        sessionId, isValid, isExpired)
                    return@fromCallable false
                }
                
                return@fromCallable true
            } catch (e: Exception) {
                logger.error("Error validating token for session: {}", sessionId, e)
                return@fromCallable false
            }
        }.subscribeOn(Schedulers.boundedElastic())
    }
    
    /**
     * 주기적 토큰 검증 시작
     */
    fun startPeriodicValidation(): Mono<Void> {
        return Mono.fromRunnable<Void> {
            logger.info("Starting periodic token validation for WebSocket sessions")
        }.then(
            Mono.delay(Duration.ofMinutes(1))
                .repeat()
                .flatMap { _: Long -> validateAllSessions() }
                .then()
        )
    }
    
    /**
     * 모든 세션의 토큰 검증
     */
    private fun validateAllSessions(): Mono<Void> {
        return Mono.fromRunnable<Void> {
            val invalidSessions = mutableListOf<String>()
            
            sessionTokens.forEach { (sessionId, token) ->
                try {
                    if (!jwtTokenProvider.validateToken(token) || jwtTokenProvider.isTokenExpired(token)) {
                        invalidSessions.add(sessionId)
                    }
                } catch (e: Exception) {
                    logger.error("Error during periodic validation for session: {}", sessionId, e)
                    invalidSessions.add(sessionId)
                }
            }
            
            // 유효하지 않은 세션들 제거
            invalidSessions.forEach { sessionId ->
                sessionTokens.remove(sessionId)
                logger.info("Removed invalid token for session: {}", sessionId)
            }
            
            if (invalidSessions.isNotEmpty()) {
                logger.info("Removed {} invalid tokens during periodic validation", invalidSessions.size)
            }
        }.subscribeOn(Schedulers.boundedElastic())
    }
    
    /**
     * 특정 세션의 토큰 갱신
     */
    fun updateSessionToken(sessionId: String, newToken: String): Boolean {
        return if (sessionTokens.containsKey(sessionId)) {
            sessionTokens[sessionId] = newToken
            logger.debug("Token updated for session: {}", sessionId)
            true
        } else {
            logger.warn("Attempted to update token for non-existent session: {}", sessionId)
            false
        }
    }
    
    /**
     * 현재 활성 세션 수 반환
     */
    fun getActiveSessionCount(): Int {
        return sessionTokens.size
    }
    
    /**
     * 세션의 토큰 만료 시간 반환
     */
    fun getTokenExpirationTime(sessionId: String): Long? {
        val token = sessionTokens[sessionId] ?: return null
        return try {
            jwtTokenProvider.getExpirationFromToken(token)?.time
        } catch (e: Exception) {
            logger.error("Error getting expiration time for session: {}", sessionId, e)
            null
        }
    }
}