package com.simplechat.service

import com.simplechat.domain.entity.User
import com.simplechat.domain.exception.auth.AuthenticationException
import com.simplechat.domain.exception.database.DatabaseException
import com.simplechat.domain.exception.ErrorCode
import com.simplechat.domain.exception.auth.JwtAuthenticationException
import com.simplechat.domain.exception.entity.ResourceNotFoundException
import com.simplechat.domain.exception.ValidationException
import com.simplechat.domain.repository.UserRepository
import com.simplechat.dto.auth.AuthResponse
import com.simplechat.dto.auth.LoginRequest
import com.simplechat.dto.auth.RefreshTokenRequest
import com.simplechat.dto.auth.RefreshTokenResponse
import com.simplechat.dto.auth.SignUpRequest
import com.simplechat.dto.auth.UserDto
import com.simplechat.dto.session.UserSessionInfo
import com.simplechat.infrastructure.config.JwtProperties
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
    private val webSocketMessageHandler: WebSocketMessageHandler
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
        
        // 중복 로그인 감지
        val duplicateLoginResult = webSocketSessionManager.detectDuplicateLogin(user.id!!)
        if (duplicateLoginResult.hasDuplicateLogin) {
            logger.info("Duplicate login detected for user: {} (existing sessions: {})", 
                user.id, duplicateLoginResult.existingSessionCount)
            
            // 중복 로그인 알림 전송
            val newLoginInfo = NewLoginInfo(
                loginTime = Instant.now(),
                ipAddress = null, // HTTP 요청에서 추출 가능
                userAgent = null  // HTTP 요청에서 추출 가능
            )
            
            // 기존 세션들에게 알림 전송
            webSocketSessionManager.notifyDuplicateLogin(user.id!!, newLoginInfo, webSocketMessageHandler)
            
            // 선택: 기존 세션들을 강제 로그아웃 (설정에 따라)
            // sessionInvalidationService.invalidateAllUserSessions(user.id!!)
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
     * 인증 응답 생성
     */
    private fun generateAuthResponse(user: User): AuthResponse {
        val accessToken = jwtTokenProvider.generateAccessToken(user.email, user.id!!, listOf("USER"))
        val refreshToken = jwtTokenProvider.generateRefreshToken(user.email, user.id!!, listOf("USER"))
        
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
        
        // 중복 로그인 감지
        val duplicateLoginResult = webSocketSessionManager.detectDuplicateLogin(user.id!!)
        if (duplicateLoginResult.hasDuplicateLogin) {
            logger.info("Duplicate login detected for user: {} from IP: {} (existing sessions: {})", 
                user.id, ipAddress ?: "unknown", duplicateLoginResult.existingSessionCount)
            
            // 중복 로그인 알림 전송 (클라이언트 정보 포함)
            val newLoginInfo = NewLoginInfo(
                loginTime = Instant.now(),
                ipAddress = ipAddress,
                userAgent = userAgent
            )
            
            webSocketSessionManager.notifyDuplicateLogin(user.id!!, newLoginInfo, webSocketMessageHandler)
        }
        
        return generateAuthResponse(user)
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