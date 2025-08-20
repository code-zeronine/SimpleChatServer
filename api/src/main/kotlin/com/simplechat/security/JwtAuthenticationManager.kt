package com.simplechat.security

import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class JwtAuthenticationManager : ReactiveAuthenticationManager {

    override fun authenticate(authentication: Authentication): Mono<Authentication> {
        // TODO: JWT 토큰 검증 로직 구현 예정
        // 현재는 모든 인증을 허용 (개발 단계)
        return Mono.just(authentication)
    }
}