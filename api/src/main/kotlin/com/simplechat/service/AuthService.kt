package com.simplechat.service

import com.simplechat.domain.entity.User
import com.simplechat.domain.exception.ErrorCode
import com.simplechat.domain.exception.ValidationException
import com.simplechat.domain.exception.auth.AuthenticationException
import com.simplechat.domain.exception.auth.JwtAuthenticationException
import com.simplechat.domain.exception.database.DatabaseException
import com.simplechat.domain.exception.entity.ResourceNotFoundException
import com.simplechat.domain.repository.UserRepository
import com.simplechat.dto.auth.AuthResponse
import com.simplechat.dto.auth.LoginRequest
import com.simplechat.dto.auth.RefreshTokenRequest
import com.simplechat.dto.auth.RefreshTokenResponse
import com.simplechat.dto.auth.SignUpRequest
import com.simplechat.dto.auth.UserDto
import com.simplechat.dto.session.UserSessionInfo
import com.simplechat.infrastructure.config.DuplicateLoginConfig
import com.simplechat.infrastructure.config.DuplicateLoginConfigAction
import com.simplechat.infrastructure.config.JwtProperties
import com.simplechat.infrastructure.security.jwt.JwtSessionService
import com.simplechat.infrastructure.security.jwt.JwtTokenProvider
import com.simplechat.infrastructure.session.WebSocketSessionManager
import com.simplechat.infrastructure.session.model.NewLoginInfo
import com.simplechat.infrastructure.websocket.handler.WebSocketMessageHandler
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * 인증 관련 비즈니스 로직을 담당하는 서비스
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
    private val jwtProperties: JwtProperties,
    private val sessionInvalidationService: SessionInvalidationService,
    private val webSocketSessionManager: WebSocketSessionManager,
    private val webSocketMessageHandler: WebSocketMessageHandler,
    private val duplicateLoginConfig: DuplicateLoginConfig,
    private val jwtSessionService: JwtSessionService
) {

    private val logger = LoggerFactory.getLogger(AuthService::class.java)

    /**
     * 회원가입 처리
     */
    suspend fun signUp(request: SignUpRequest): AuthResponse {
        validateSignUpRequest(request)
        val user = createUser(request)
        return generateAuthResponse(user)
    }

    /**
     * 로그인 처리 (중복 로그인 제어 포함)
     */
    suspend fun login(request: LoginRequest): AuthResponse {
        val user = userRepository.findByEmail(request.email).awaitSingleOrNull()
            ?: throw AuthenticationException("이메일 또는 비밀번호가 올바르지 않습니다.", ErrorCode.AUTHENTICATION_FAILED)

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw AuthenticationException("이메일 또는 비밀번호가 올바르지 않습니다.", ErrorCode.AUTHENTICATION_FAILED)
        }
        
        // JWT 기반 중복 로그인 감지 및 제어
        if (duplicateLoginConfig.enabled) {
            handleJwtBasedDuplicateLogin(user.id!!, user.email, null, null)
        }
        
        return generateAuthResponse(user)
    }

    /**
     * 토큰 갱신 처리
     */
    suspend fun refreshToken(request: RefreshTokenRequest): RefreshTokenResponse {
        try {
            val newAccessToken = jwtTokenProvider.refreshAccessToken(request.refreshToken)
                ?: throw JwtAuthenticationException("토큰 갱신에 실패했습니다. 유효하지 않거나 만료된 리프레시 토큰입니다.", ErrorCode.JWT_AUTHENTICATION_FAILED)
            
            return RefreshTokenResponse(
                accessToken = newAccessToken,
                expiresIn = jwtProperties.expiration
            )
        } catch (e: JwtAuthenticationException) {
            throw e
        } catch (e: Exception) {
            throw JwtAuthenticationException("토큰 갱신 중 오류가 발생했습니다.", ErrorCode.JWT_AUTHENTICATION_FAILED, e)
        }
    }

    /**
     * 사용자 검증 (토큰 기반)
     */
    suspend fun validateUser(token: String): UserDto {
        if (!jwtTokenProvider.validateToken(token)) {
            throw JwtAuthenticationException("유효하지 않은 토큰입니다.", ErrorCode.JWT_AUTHENTICATION_FAILED)
        }
        val email = jwtTokenProvider.getEmailFromToken(token)
        val user = userRepository.findByEmail(email).awaitSingleOrNull()
            ?: throw ResourceNotFoundException("사용자를 찾을 수 없습니다.", ErrorCode.USER_NOT_FOUND)
        return user.toDto()
    }

    /**
     * 이메일 중복 확인
     */
    suspend fun checkEmailExists(email: String): Boolean {
        return userRepository.existsByEmail(email).awaitSingle()
    }

    /**
     * 닉네임 중복 확인
     */
    suspend fun checkNicknameExists(nickname: String): Boolean {
        return userRepository.existsByNickname(nickname).awaitSingle()
    }

    suspend fun updateNickname(email: String, newNickname: String): UserDto {
        val user = userRepository.findByEmail(email).awaitSingleOrNull()
            ?: throw ResourceNotFoundException("사용자를 찾을 수 없습니다.", ErrorCode.USER_NOT_FOUND)
        
        val updatedUser = userRepository.save(user.copy(nickname = newNickname)).awaitSingle()
        return updatedUser.toDto()
    }

    /**
     * 회원가입 요청 검증
     */
    private suspend fun validateSignUpRequest(request: SignUpRequest) {
        if (checkEmailExists(request.email)) {
            throw ValidationException("이미 사용 중인 이메일입니다.", "email", ErrorCode.DUPLICATE_EMAIL)
        }
        if (checkNicknameExists(request.nickname)) {
            throw ValidationException("이미 사용 중인 닉네임입니다.", "nickname", ErrorCode.DUPLICATE_NICKNAME)
        }
    }

    /**
     * 사용자 생성
     */
    private suspend fun createUser(request: SignUpRequest): User {
        val encodedPassword = passwordEncoder.encode(request.password)
        val user = User(
            email = request.email,
            passwordHash = encodedPassword,
            nickname = request.nickname
        )
        
        try {
            return userRepository.save(user).awaitSingle()
        } catch (exception: Exception) {
            throw DatabaseException("사용자 생성 중 오류가 발생했습니다.", ErrorCode.DATABASE_ERROR, exception)
        }
    }

    /**
     * 인증 응답 생성 및 JWT 세션 등록
     */
    private suspend fun generateAuthResponse(user: User): AuthResponse {
        val accessToken = jwtTokenProvider.generateAccessToken(user.email, user.id!!, listOf("USER"))
        val refreshToken = jwtTokenProvider.generateRefreshToken(user.email, user.id!!, listOf("USER"))
        
        // JWT 세션 등록
        try {
            jwtSessionService.registerSession(
                token = accessToken,
                userId = user.id!!,
                email = user.email,
                expirationDuration = java.time.Duration.ofMillis(jwtProperties.expiration)
            ).awaitSingleOrNull()
            
            logger.debug("JWT session registered successfully for user: {}", user.id)
        } catch (e: Exception) {
            logger.error("Failed to register JWT session for user {}: {}", user.id, e.message)
            // 세션 등록 실패해도 로그인은 계속 진행
        }
        
        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = jwtProperties.expiration,
            user = user.toDto()
        )
    }

    /**
     * 사용자의 활성 세션 정보 조회
     */
    suspend fun getUserActiveSessions(email: String): UserSessionInfo {
        val user = userRepository.findByEmail(email).awaitSingleOrNull()
            ?: throw ResourceNotFoundException("사용자를 찾을 수 없습니다.", ErrorCode.USER_NOT_FOUND)
        
        return sessionInvalidationService.getUserSessionInfo(user.id!!)
    }

    /**
     * 특정 세션 강제 로그아웃
     */
    suspend fun forceLogoutSession(email: String, sessionId: String): Boolean {
        val user = userRepository.findByEmail(email).awaitSingleOrNull()
            ?: throw ResourceNotFoundException("사용자를 찾을 수 없습니다.", ErrorCode.USER_NOT_FOUND)
        
        // 해당 세션이 사용자의 세션인지 확인
        val userSessionInfo = sessionInvalidationService.getUserSessionInfo(user.id!!)
        userSessionInfo.sessions.find { it.sessionId == sessionId }
            ?: throw ValidationException("해당 세션을 찾을 수 없습니다.", "sessionId", ErrorCode.RESOURCE_NOT_FOUND)
        
        return sessionInvalidationService.invalidateSession(sessionId)
    }

    /**
     * 모든 다른 세션 강제 로그아웃 (현재 세션 제외)
     */
    suspend fun forceLogoutAllOtherSessions(email: String, currentSessionId: String? = null): Int {
        val user = userRepository.findByEmail(email).awaitSingleOrNull()
            ?: throw ResourceNotFoundException("사용자를 찾을 수 없습니다.", ErrorCode.USER_NOT_FOUND)
        
        return if (currentSessionId != null) {
            sessionInvalidationService.invalidateOtherUserSessions(user.id!!, currentSessionId)
        } else {
            sessionInvalidationService.invalidateAllUserSessions(user.id!!)
        }
    }

    /**
     * IP 주소와 User-Agent를 포함한 고급 로그인 처리
     */
    suspend fun loginWithClientInfo(request: LoginRequest, ipAddress: String?, userAgent: String?): AuthResponse {
        val user = userRepository.findByEmail(request.email).awaitSingleOrNull()
            ?: throw AuthenticationException("이메일 또는 비밀번호가 올바르지 않습니다.", ErrorCode.AUTHENTICATION_FAILED)

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw AuthenticationException("이메일 또는 비밀번호가 올바르지 않습니다.", ErrorCode.AUTHENTICATION_FAILED)
        }
        
        // JWT 기반 중복 로그인 감지 및 제어
        if (duplicateLoginConfig.enabled) {
            handleJwtBasedDuplicateLogin(user.id!!, user.email, ipAddress, userAgent)
        }
        
        return generateAuthResponse(user)
    }

    /**
     * JWT 기반 중복 로그인 처리 로직
     */
    private suspend fun handleJwtBasedDuplicateLogin(
        userId: Long,
        @Suppress("UNUSED_PARAMETER") email: String,
        ipAddress: String?,
        userAgent: String?
    ) {
        // 현재 활성 JWT 세션 수 조회
        val activeSessionCount = jwtSessionService.getActiveSessionCount(userId).awaitSingle()
        
        logger.debug("User {} has {} active JWT sessions", userId, activeSessionCount)
        
        val newLoginInfo = NewLoginInfo(
            loginTime = Instant.now(),
            ipAddress = ipAddress,
            userAgent = userAgent
        )

        when (duplicateLoginConfig.action) {
            DuplicateLoginConfigAction.DENY_NEW_LOGIN -> {
                // 최대 세션 수 체크 (0은 무제한)
                if (duplicateLoginConfig.maxConcurrentSessions in 1..activeSessionCount) {
                    
                    logger.warn("Login denied for user {} - max JWT sessions exceeded ({}/{})", 
                        userId, activeSessionCount, duplicateLoginConfig.maxConcurrentSessions)
                    
                    throw AuthenticationException(
                        "동시 로그인 세션 수를 초과했습니다. 기존 세션을 종료한 후 다시 시도하세요. (현재: ${activeSessionCount}/${duplicateLoginConfig.maxConcurrentSessions})",
                        ErrorCode.MAX_SESSION_EXCEEDED
                    )
                }
            }
            
            DuplicateLoginConfigAction.FORCE_LOGOUT_OTHERS -> {
                logger.info("Force logging out other JWT sessions for user: {}", userId)
                
                // 모든 기존 JWT 세션 무효화
                val invalidatedCount = jwtSessionService.invalidateAllSessions(userId).awaitSingle()
                logger.info("Invalidated {} JWT sessions for user: {}", invalidatedCount, userId)
                
                // WebSocket 세션도 무효화
                sessionInvalidationService.invalidateAllUserSessions(userId)
                
                // 알림도 전송
                webSocketSessionManager.notifyDuplicateLogin(userId, newLoginInfo, webSocketMessageHandler)
            }
            
            DuplicateLoginConfigAction.NOTIFY_ONLY -> {
                // 기존 세션들에게 알림만 전송
                if (activeSessionCount > 0) {
                    webSocketSessionManager.notifyDuplicateLogin(userId, newLoginInfo, webSocketMessageHandler)
                }
            }
            
            DuplicateLoginConfigAction.ASK_USER_CHOICE -> {
                // 사용자에게 선택권 제공 알림 전송
                if (activeSessionCount > 0) {
                    webSocketSessionManager.notifyDuplicateLogin(userId, newLoginInfo, webSocketMessageHandler)
                }
                
                // 실제 구현에서는 클라이언트에서 응답을 기다리는 로직이 필요
                // 현재는 알림만 전송하고 로그인 허용
                logger.info("User choice required for duplicate login - user: {} (active sessions: {})", userId, activeSessionCount)
            }
        }
    }

    /**
     * 기존 WebSocket 기반 중복 로그인 처리 로직 (호환성 유지)
     */
    private suspend fun handleDuplicateLogin(
        userId: Long, 
        duplicateLoginResult: com.simplechat.infrastructure.session.model.DuplicateLoginResult,
        ipAddress: String?,
        userAgent: String?
    ) {
        val newLoginInfo = NewLoginInfo(
            loginTime = Instant.now(),
            ipAddress = ipAddress,
            userAgent = userAgent
        )

        when (duplicateLoginConfig.action) {
            DuplicateLoginConfigAction.DENY_NEW_LOGIN -> {
                // 최대 세션 수 체크 (0은 무제한)
                if (duplicateLoginConfig.maxConcurrentSessions > 0 && 
                    duplicateLoginResult.existingSessionCount >= duplicateLoginConfig.maxConcurrentSessions) {
                    
                    logger.warn("Login denied for user {} - max sessions exceeded ({}/{})", 
                        userId, duplicateLoginResult.existingSessionCount, duplicateLoginConfig.maxConcurrentSessions)
                    
                    throw AuthenticationException(
                        "동시 로그인 세션 수를 초과했습니다. 기존 세션을 종료한 후 다시 시도하세요. (현재: ${duplicateLoginResult.existingSessionCount}/${duplicateLoginConfig.maxConcurrentSessions})",
                        ErrorCode.MAX_SESSION_EXCEEDED
                    )
                }
            }
            
            DuplicateLoginConfigAction.FORCE_LOGOUT_OTHERS -> {
                logger.info("Force logging out other sessions for user: {}", userId)
                sessionInvalidationService.invalidateAllUserSessions(userId)
                
                // 알림도 전송
                webSocketSessionManager.notifyDuplicateLogin(userId, newLoginInfo, webSocketMessageHandler)
            }
            
            DuplicateLoginConfigAction.NOTIFY_ONLY -> {
                // 기존 세션들에게 알림만 전송
                webSocketSessionManager.notifyDuplicateLogin(userId, newLoginInfo, webSocketMessageHandler)
            }
            
            DuplicateLoginConfigAction.ASK_USER_CHOICE -> {
                // 사용자에게 선택권 제공 알림 전송
                webSocketSessionManager.notifyDuplicateLogin(userId, newLoginInfo, webSocketMessageHandler)
                
                // 실제 구현에서는 클라이언트에서 응답을 기다리는 로직이 필요
                // 현재는 알림만 전송하고 로그인 허용
                logger.info("User choice required for duplicate login - user: {}", userId)
            }
        }
    }

    /**
     * User를 UserDto로 변환하는 확장 함수
     */
    private fun User.toDto(): UserDto {
        return UserDto(
            id = this.id,
            email = this.email,
            nickname = this.nickname,
            createdAt = this.createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )
    }
}