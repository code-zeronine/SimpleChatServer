package com.simplechat.infrastructure.security

import com.simplechat.infrastructure.config.WebSocketProperties
import com.simplechat.infrastructure.security.jwt.JwtTokenProvider
import com.simplechat.infrastructure.security.jwt.JwtUserDetails
import com.simplechat.infrastructure.service.WebSocketSessionManager
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Service("webSocketAuthService")
class WebSocketAuthService(
    private val jwtTokenProvider: JwtTokenProvider,
    private val webSocketProperties: WebSocketProperties,
    private val sessionManager: WebSocketSessionManager
) : HandshakeWebSocketService() {

    private val logger = LoggerFactory.getLogger(WebSocketAuthService::class.java)
    
    // 전체 연결 수 추적
    private val totalConnections = AtomicInteger(0)
    
    // 사용자별 연결 수 추적
    private val userConnections = ConcurrentHashMap<Long, AtomicInteger>()

    override fun handleRequest(exchange: ServerWebExchange, handler: WebSocketHandler): Mono<Void> {
        val token = extractToken(exchange)
        
        // 1. 연결 제한 검사
        if (totalConnections.get() >= webSocketProperties.maxConnections) {
            logger.warn("Maximum total connections ({}) reached, rejecting WebSocket connection", 
                webSocketProperties.maxConnections)
            exchange.response.statusCode = HttpStatus.SERVICE_UNAVAILABLE
            return exchange.response.setComplete()
        }

        return if (token != null && validateTokenWithLogging(token)) {
            try {
                val email = jwtTokenProvider.getEmailFromToken(token)
                val userId = jwtTokenProvider.getUserIdFromToken(token)
                val roles = jwtTokenProvider.getRolesFromToken(token)
                val authorities = roles.map { SimpleGrantedAuthority("ROLE_$it") }
                
                // 2. 사용자별 연결 수 제한 검사
                val userConnectionCount = userConnections.computeIfAbsent(userId) { AtomicInteger(0) }
                if (userConnectionCount.get() >= webSocketProperties.maxConnectionsPerUser) {
                    logger.warn("Maximum connections per user ({}) reached for user {}, rejecting connection", 
                        webSocketProperties.maxConnectionsPerUser, userId)
                    exchange.response.statusCode = HttpStatus.TOO_MANY_REQUESTS
                    return exchange.response.setComplete()
                }
                
                val userDetails = JwtUserDetails(
                    id = userId,
                    username = email,
                    password = "",
                    authorities = authorities
                )
                
                val authentication = UsernamePasswordAuthenticationToken(userDetails, null, authorities)
                
                // 3. 연결 카운터 증가
                totalConnections.incrementAndGet()
                userConnectionCount.incrementAndGet()
                
                logger.info("WebSocket authentication successful for user: {} (ID: {}), total connections: {}", 
                    email, userId, totalConnections.get())
                
                super.handleRequest(exchange, handler)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
                    .doOnTerminate {
                        // 연결 종료 시 카운터 감소
                        decrementConnection(userId)
                        logger.info("WebSocket connection closed for user: {} (ID: {}), total connections: {}", 
                            email, userId, totalConnections.get())
                    }
            } catch (e: Exception) {
                logger.error("Failed to process authenticated user from token", e)
                rejectConnection(exchange, "Invalid token data")
            }
        } else {
            val reason = if (token == null) "No token provided" else "Invalid or expired token"
            logger.warn("WebSocket authentication failed: {}", reason)
            rejectConnection(exchange, reason)
        }
    }

    private fun extractToken(exchange: ServerWebExchange): String? {
        // 1. Authorization 헤더에서 추출 시도
        val authHeader = exchange.request.headers.getFirst("Authorization")
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            logger.debug("Token extracted from Authorization header")
            return authHeader.substring(7)
        }

        // 2. Query 파라미터에서 추출 시도
        val query = exchange.request.uri.query
        if (query != null) {
            val queryParams = query.split('&').associate {
                val parts = it.split('=', limit = 2)
                parts[0] to parts.getOrNull(1)
            }
            val tokenParam = queryParams["token"]
            if (tokenParam != null) {
                logger.debug("Token extracted from query parameter")
                return tokenParam
            }
        }

        // 3. Sec-WebSocket-Protocol 헤더에서 추출 시도 (일부 클라이언트에서 사용)
        val protocolHeader = exchange.request.headers.getFirst("Sec-WebSocket-Protocol")
        if (protocolHeader != null && protocolHeader.contains("access_token")) {
            val protocols = protocolHeader.split(", ")
            for (protocol in protocols) {
                if (protocol.startsWith("access_token.")) {
                    logger.debug("Token extracted from Sec-WebSocket-Protocol header")
                    return protocol.substring(13) // "access_token." 이후 부분
                }
            }
        }

        logger.debug("No token found in request")
        return null
    }

    private fun validateTokenWithLogging(token: String): Boolean {
        return try {
            val isValid = jwtTokenProvider.validateToken(token)
            if (!isValid) {
                logger.warn("Token validation failed")
            }
            
            // 토큰 타입 검증 (액세스 토큰만 허용)
            if (isValid && !jwtTokenProvider.isAccessToken(token)) {
                logger.warn("Non-access token provided for WebSocket authentication")
                return false
            }
            
            // 토큰 만료 검증
            if (isValid && jwtTokenProvider.isTokenExpired(token)) {
                logger.warn("Expired token provided for WebSocket authentication")
                return false
            }
            
            isValid
        } catch (e: Exception) {
            logger.error("Token validation error", e)
            false
        }
    }

    private fun rejectConnection(exchange: ServerWebExchange, reason: String): Mono<Void> {
        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
        exchange.response.headers.add("X-Auth-Error", reason)
        return exchange.response.setComplete()
    }
    
    /**
     * 연결 종료 시 카운터를 감소시킵니다.
     */
    private fun decrementConnection(userId: Long) {
        totalConnections.decrementAndGet()
        userConnections[userId]?.decrementAndGet()?.let { count ->
            if (count <= 0) {
                userConnections.remove(userId)
            }
        }
    }
    
    /**
     * 연결 통계 정보를 반환합니다.
     */
    fun getConnectionStats(): Map<String, Any> {
        return mapOf(
            "totalConnections" to totalConnections.get(),
            "maxConnections" to webSocketProperties.maxConnections,
            "activeUsers" to userConnections.size,
            "maxConnectionsPerUser" to webSocketProperties.maxConnectionsPerUser,
            "userConnections" to userConnections.mapValues { it.value.get() }
        )
    }
}
