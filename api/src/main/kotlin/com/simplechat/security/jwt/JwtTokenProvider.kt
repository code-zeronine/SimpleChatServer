package com.simplechat.security.jwt

import com.simplechat.config.JwtProperties
import com.simplechat.exception.JwtAuthenticationException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.UnsupportedJwtException
import io.jsonwebtoken.security.SignatureException
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.util.*
import javax.crypto.SecretKey

/**
 * JWT 토큰 생성, 검증, 갱신을 담당하는 핵심 컴포넌트
 */
@Component
class JwtTokenProvider(
    private val jwtProperties: JwtProperties
) {
    
    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())
    }

    /**
     * 액세스 토큰 생성
     */
    fun generateAccessToken(username: String, roles: List<String> = emptyList()): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtProperties.expiration)

        return Jwts.builder()
            .subject(username)
            .issuer(jwtProperties.issuer)
            .issuedAt(now)
            .expiration(expiryDate)
            .claim("roles", roles)
            .claim("type", "access")
            .signWith(key)
            .compact()
    }

    /**
     * 리프레시 토큰 생성
     */
    fun generateRefreshToken(username: String): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtProperties.refreshExpiration)

        return Jwts.builder()
            .subject(username)
            .issuer(jwtProperties.issuer)
            .issuedAt(now)
            .expiration(expiryDate)
            .claim("type", "refresh")
            .signWith(key)
            .compact()
    }

    /**
     * 토큰에서 사용자명 추출
     */
    fun getUsernameFromToken(token: String): String {
        return try {
            val claims = parseToken(token)
            claims.subject
        } catch (e: Exception) {
            throw JwtAuthenticationException("Invalid token: Unable to extract username", e)
        }
    }

    /**
     * 토큰에서 권한 목록 추출
     */
    fun getRolesFromToken(token: String): List<String> {
        return try {
            val claims = parseToken(token)
            val roles = claims["roles"] as? List<*>
            roles?.filterIsInstance<String>() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 토큰 유효성 검증
     */
    fun validateToken(token: String): Boolean {
        return try {
            parseToken(token)
            true
        } catch (e: JwtAuthenticationException) {
            false
        } catch (e: JwtException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 액세스 토큰인지 확인
     */
    fun isAccessToken(token: String): Boolean {
        return try {
            val claims = parseToken(token)
            claims["type"] == "access"
        } catch (e: JwtAuthenticationException) {
            false
        } catch (e: JwtException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 리프레시 토큰인지 확인
     */
    fun isRefreshToken(token: String): Boolean {
        return try {
            val claims = parseToken(token)
            claims["type"] == "refresh"
        } catch (e: JwtAuthenticationException) {
            false
        } catch (e: JwtException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 토큰 만료 확인
     */
    fun isTokenExpired(token: String): Boolean {
        return try {
            val claims = parseToken(token)
            val expiration = claims.expiration
            expiration.before(Date())
        } catch (e: JwtAuthenticationException) {
            true
        } catch (e: JwtException) {
            true
        } catch (e: Exception) {
            true
        }
    }

    /**
     * 토큰에서 만료 시간 추출
     */
    fun getExpirationFromToken(token: String): Date? {
        return try {
            val claims = parseToken(token)
            claims.expiration
        } catch (e: JwtAuthenticationException) {
            null
        } catch (e: JwtException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 토큰 파싱
     */
    private fun parseToken(token: String): Claims {
        return try {
            Jwts.parser()
                .verifyWith(key)
                .requireIssuer(jwtProperties.issuer)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (e: ExpiredJwtException) {
            throw JwtAuthenticationException("Token has expired", e)
        } catch (e: UnsupportedJwtException) {
            throw JwtAuthenticationException("Unsupported JWT token", e)
        } catch (e: MalformedJwtException) {
            throw JwtAuthenticationException("Malformed JWT token", e)
        } catch (e: SignatureException) {
            throw JwtAuthenticationException("Invalid JWT signature", e)
        } catch (e: IllegalArgumentException) {
            throw JwtAuthenticationException("JWT token compact of handler are invalid", e)
        }
    }

    /**
     * Bearer 접두사 제거
     */
    fun resolveToken(bearerToken: String?): String? {
        return if (bearerToken != null && bearerToken.startsWith("${jwtProperties.prefix} ")) {
            bearerToken.substring(7)
        } else {
            null
        }
    }

    /**
     * 토큰 갱신 (리프레시 토큰으로 새 액세스 토큰 생성)
     */
    fun refreshAccessToken(refreshToken: String): String? {
        return if (validateToken(refreshToken) && isRefreshToken(refreshToken)) {
            val username = getUsernameFromToken(refreshToken)
            generateAccessToken(username)
        } else {
            null
        }
    }
}