package com.simplechat.service

import com.simplechat.infrastructure.config.JwtProperties
import com.simplechat.domain.entity.User
import com.simplechat.dto.AuthResponse
import com.simplechat.dto.LoginRequest
import com.simplechat.dto.RefreshTokenRequest
import com.simplechat.dto.RefreshTokenResponse
import com.simplechat.dto.SignUpRequest
import com.simplechat.dto.UserDto
import com.simplechat.infrastructure.exception.*
import com.simplechat.infrastructure.exception.ErrorCode
import com.simplechat.domain.repository.UserRepository
import com.simplechat.infrastructure.security.jwt.JwtTokenProvider
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.format.DateTimeFormatter

/**
 * 인증 관련 비즈니스 로직을 담당하는 서비스
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
    private val jwtProperties: JwtProperties
) {

    /**
     * 회원가입 처리
     *
     */
    fun signUp(request: SignUpRequest): Mono<AuthResponse> {
        return validateSignUpRequest(request)
            .flatMap {
                createUser(request)
            }
            .flatMap { user ->
                generateAuthResponse(user)
            }
    }

    /**
     * 로그인 처리
     */
    fun login(request: LoginRequest): Mono<AuthResponse> {
        return userRepository.findByEmail(request.email)
            .switchIfEmpty(Mono.error(AuthenticationException("이메일 또는 비밀번호가 올바르지 않습니다.", ErrorCode.AUTHENTICATION_FAILED)))
            .filter { user -> passwordEncoder.matches(request.password, user.passwordHash) }
            .switchIfEmpty(Mono.error(AuthenticationException("이메일 또는 비밀번호가 올바르지 않습니다.", ErrorCode.AUTHENTICATION_FAILED)))
            .flatMap { user ->
                generateAuthResponse(user)
            }
    }

    /**
     * 토큰 갱신 처리
     */
    fun refreshToken(request: RefreshTokenRequest): Mono<RefreshTokenResponse> {
        return Mono.fromCallable {
            if (!jwtTokenProvider.validateToken(request.refreshToken)) {
                throw JwtAuthenticationException("유효하지 않은 리프레시 토큰입니다.", ErrorCode.JWT_AUTHENTICATION_FAILED)
            }
            
            if (!jwtTokenProvider.isRefreshToken(request.refreshToken)) {
                throw JwtAuthenticationException("리프레시 토큰이 아닙니다.", ErrorCode.JWT_AUTHENTICATION_FAILED)
            }
            
            val email = jwtTokenProvider.getEmailFromToken(request.refreshToken)
            val newAccessToken = jwtTokenProvider.generateAccessToken(email, listOf("USER"))
            
            RefreshTokenResponse(
                accessToken = newAccessToken,
                expiresIn = jwtProperties.expiration
            )
        }
    }

    /**
     * 사용자 검증 (토큰 기반)
     */
    fun validateUser(token: String): Mono<UserDto> {
        return Mono.fromCallable {
            if (!jwtTokenProvider.validateToken(token)) {
                throw JwtAuthenticationException("유효하지 않은 토큰입니다.", ErrorCode.JWT_AUTHENTICATION_FAILED)
            }
            
            val email = jwtTokenProvider.getEmailFromToken(token)
            email
        }.flatMap { email ->
            userRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(ResourceNotFoundException("사용자를 찾을 수 없습니다.", ErrorCode.USER_NOT_FOUND)))
                .map { user -> user.toDto() }
        }
    }

    /**
     * 이메일 중복 확인
     */
    fun checkEmailExists(email: String): Mono<Boolean> {
        return userRepository.existsByEmail(email)
    }

    /**
     * 닉네임 중복 확인
     */
    fun checkNicknameExists(nickname: String): Mono<Boolean> {
        return userRepository.existsByNickname(nickname)
    }

    /**
     * 회원가입 요청 검증
     */
    private fun validateSignUpRequest(request: SignUpRequest): Mono<Void> {
        return checkEmailExists(request.email)
            .flatMap { emailExists ->
                if (emailExists) {
                    Mono.error<Void>(ValidationException("이미 사용 중인 이메일입니다.", "email", ErrorCode.DUPLICATE_EMAIL))
                } else {
                    checkNicknameExists(request.nickname)
                        .flatMap { nicknameExists ->
                            if (nicknameExists) {
                                Mono.error<Void>(ValidationException("이미 사용 중인 닉네임입니다.", "nickname", ErrorCode.DUPLICATE_NICKNAME))
                            } else {
                                Mono.empty<Void>()
                            }
                        }
                }
            }
    }

    /**
     * 사용자 생성
     */
    private fun createUser(request: SignUpRequest): Mono<User> {
        val encodedPassword = passwordEncoder.encode(request.password)
        val user = User(
            email = request.email,
            passwordHash = encodedPassword,
            nickname = request.nickname
        )
        
        return userRepository.save(user)
            .onErrorMap { exception ->
                DatabaseException("사용자 생성 중 오류가 발생했습니다.", ErrorCode.DATABASE_ERROR, exception)
            }
    }

    /**
     * 인증 응답 생성
     */
    private fun generateAuthResponse(user: User): Mono<AuthResponse> {
        return Mono.fromCallable {
            val accessToken = jwtTokenProvider.generateAccessToken(user.email, listOf("USER"))
            val refreshToken = jwtTokenProvider.generateRefreshToken(user.email)
            
            AuthResponse(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresIn = jwtProperties.expiration,
                user = user.toDto()
            )
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