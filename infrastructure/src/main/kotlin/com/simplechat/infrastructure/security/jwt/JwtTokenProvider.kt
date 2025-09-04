package com.simplechat.infrastructure.security.jwt

import com.simplechat.domain.exception.ErrorCode
import com.simplechat.domain.exception.JwtAuthenticationException
import com.simplechat.infrastructure.config.JwtProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.UnsupportedJwtException
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.SignatureException
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
    fun generateAccessToken(email: String, userId: Long, roles: List<String> = emptyList()): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtProperties.expiration)

        return Jwts.builder()
            .subject(email)
            .issuer(jwtProperties.issuer)
            .issuedAt(now)
            .expiration(expiryDate)
            .claim("userId", userId)
            .claim("roles", roles)
            .claim("type", "access")
            .signWith(key)
            .compact()
    }

    /**
     * 액세스 토큰 생성 (userId 없는 버전 - 호환성)
     */
    fun generateAccessToken(email: String, roles: List<String> = emptyList()): String {
        return generateAccessToken(email, 0L, roles)
    }

    /**
     * 리프레시 토큰 생성
     */
    fun generateRefreshToken(email: String): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtProperties.refreshExpiration)

        return Jwts.builder()
            .subject(email)
            .issuer(jwtProperties.issuer)
            .issuedAt(now)
            .expiration(expiryDate)
            .claim("type", "refresh")
            .signWith(key)
            .compact()
    }
    
    /**
     * 리프레시 토큰 생성 (userId 포함)
     */
    fun generateRefreshToken(email: String, userId: Long, roles: List<String> = emptyList()): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtProperties.refreshExpiration)

        return Jwts.builder()
            .subject(email)
            .issuer(jwtProperties.issuer)
            .issuedAt(now)
            .expiration(expiryDate)
            .claim("userId", userId)
            .claim("roles", roles)
            .claim("type", "refresh")
            .signWith(key)
            .compact()
    }

    /**
     * 토큰에서 사용자명 추출
     */
    fun getEmailFromToken(token: String): String {
        return try {
            val claims = parseToken(token)
            claims.subject
        } catch (e: Exception) {
            throw JwtAuthenticationException("Invalid token: Unable to extract username", ErrorCode.JWT_AUTHENTICATION_FAILED, e)
        }
    }

    /**
     * 토큰에서 사용자 ID 추출
     */
    fun getUserIdFromToken(token: String): Long {
        return try {
            val claims = parseToken(token)
            val userId = claims["userId"]
            
            // JWT 라이브러리에서 숫자는 기본적으로 Integer로 저장됨
            val extractedUserId = when (userId) {
                is Int -> userId.toLong()
                is Long -> userId
                is Number -> userId.toLong()
                is String -> {
                    try {
                        userId.toLong()
                    } catch (e: NumberFormatException) {
                        throw JwtAuthenticationException("Invalid userId string format in token: '$userId'", ErrorCode.JWT_AUTHENTICATION_FAILED, e)
                    }
                }
                null -> throw JwtAuthenticationException("UserId claim is missing in token", ErrorCode.JWT_AUTHENTICATION_FAILED)
                else -> {
                    // 디버깅 정보 포함
                    throw JwtAuthenticationException("Unexpected userId type in token: ${userId.javaClass.simpleName}, value: $userId", ErrorCode.JWT_AUTHENTICATION_FAILED)
                }
            }
            
            // 0은 유효하지 않은 사용자 ID (호환성 메소드에서 사용됨)
            if (extractedUserId == 0L) {
                throw JwtAuthenticationException("Invalid userId in token (userId is 0). Token may have been generated without proper userId.", ErrorCode.JWT_AUTHENTICATION_FAILED)
            }
            
            extractedUserId
        } catch (e: JwtAuthenticationException) {
            // 이미 적절한 메시지를 가진 예외는 다시 던짐
            throw e
        } catch (e: Exception) {
            throw JwtAuthenticationException("Failed to extract userId from token: ${e.message}", ErrorCode.JWT_AUTHENTICATION_FAILED, e)
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
            throw JwtAuthenticationException("Token has expired", ErrorCode.JWT_AUTHENTICATION_FAILED, e)
        } catch (e: UnsupportedJwtException) {
            throw JwtAuthenticationException("Unsupported JWT token", ErrorCode.JWT_AUTHENTICATION_FAILED, e)
        } catch (e: MalformedJwtException) {
            throw JwtAuthenticationException("Malformed JWT token", ErrorCode.JWT_AUTHENTICATION_FAILED, e)
        } catch (e: SignatureException) {
            throw JwtAuthenticationException("Invalid JWT signature", ErrorCode.JWT_AUTHENTICATION_FAILED, e)
        } catch (e: IllegalArgumentException) {
            throw JwtAuthenticationException("JWT token compact of handler are invalid", ErrorCode.JWT_AUTHENTICATION_FAILED, e)
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
        return try {
            // 리프레시 토큰 유효성 검증
            if (!validateToken(refreshToken)) {
                return null
            }
            
            // 리프레시 토큰인지 확인
            if (!isRefreshToken(refreshToken)) {
                return null
            }
            
            // 토큰이 만료되었는지 확인
            if (isTokenExpired(refreshToken)) {
                return null
            }
            
            val claims = parseToken(refreshToken)
            val email = claims.subject
            
            // 리프레시 토큰에서 사용자 정보 추출
            val userId = try {
                val userIdClaim = claims["userId"]
                when (userIdClaim) {
                    is Int -> userIdClaim.toLong()
                    is Long -> userIdClaim
                    is Number -> userIdClaim.toLong()
                    is String -> userIdClaim.toLong()
                    null -> 0L // 호환성: 기존 리프레시 토큰은 userId가 없을 수 있음
                    else -> 0L
                }
            } catch (e: Exception) {
                0L // 기본값
            }
            
            val roles = try {
                val rolesClaim = claims["roles"] as? List<*>
                rolesClaim?.filterIsInstance<String>() ?: listOf("USER")
            } catch (e: Exception) {
                listOf("USER") // 기본 역할
            }
            
            // userId가 유효한 경우 포함해서 토큰 생성
            if (userId > 0) {
                generateAccessToken(email, userId, roles)
            } else {
                // 기존 호환성: userId 없이 토큰 생성
                generateAccessTokenFromRefresh(email, roles)
            }
            
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 리프레시 토큰에서 새로운 액세스 토큰 생성 (완전한 버전)
     */
    fun refreshAccessToken(refreshToken: String, userId: Long, roles: List<String> = listOf("USER")): String? {
        return try {
            // 리프레시 토큰 유효성 검증
            if (!validateToken(refreshToken)) {
                return null
            }
            
            // 리프레시 토큰인지 확인
            if (!isRefreshToken(refreshToken)) {
                return null
            }
            
            // 토큰이 만료되었는지 확인
            if (isTokenExpired(refreshToken)) {
                return null
            }
            
            val email = getEmailFromToken(refreshToken)
            
            // userId와 roles를 포함한 새로운 액세스 토큰 생성
            generateAccessToken(email, userId, roles)
            
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 리프레시 토큰에서 액세스 토큰 생성 (내부 메서드) - 호환성용
     */
    private fun generateAccessTokenFromRefresh(email: String, roles: List<String>): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtProperties.expiration)

        return Jwts.builder()
            .subject(email)
            .issuer(jwtProperties.issuer)
            .issuedAt(now)
            .expiration(expiryDate)
            .claim("userId", 0L) // 호환성: 기본값 설정 (실제로는 사용되면 안됨)
            .claim("roles", roles)
            .claim("type", "access")
            .claim("refreshed", true) // 리프레시된 토큰임을 표시
            .claim("legacy", true) // 레거시 토큰임을 표시
            .signWith(key)
            .compact()
    }
    
    /**
     * 리프레시 토큰이 최신 버전인지 확인 (userId 포함 여부)
     */
    fun isModernRefreshToken(refreshToken: String): Boolean {
        return try {
            val claims = parseToken(refreshToken)
            claims["userId"] != null
        } catch (e: Exception) {
            false
        }
    }
}