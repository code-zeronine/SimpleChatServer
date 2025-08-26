package com.simplechat.config

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Contact
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.annotations.servers.Server
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI/Swagger 문서화 설정
 */
@Configuration
@OpenAPIDefinition(
    info = Info(
        title = "SimpleChatServer API",
        description = "실시간 채팅 서버 REST API 문서",
        version = "v1.0.0",
        contact = Contact(
            name = "SimpleChatServer Team",
            email = "support@simplechat.com"
        )
    ),
    servers = [
        Server(url = "http://localhost:8080", description = "개발 서버"),
        Server(url = "https://api.simplechat.com", description = "운영 서버")
    ]
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "JWT 토큰을 사용한 Bearer 인증. 헤더에 'Authorization: Bearer <token>' 형식으로 전송"
)
class OpenApiConfig