package com.simplechat.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.RequestPredicates.GET
import org.springframework.web.reactive.function.server.RequestPredicates.path
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions.nest
import org.springframework.web.reactive.function.server.RouterFunctions.resources
import org.springframework.web.reactive.function.server.RouterFunctions.route
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

/**
 * 정적 리소스 서빙 설정
 * 
 * Spring WebFlux 환경에서 HTML, CSS, JS 등의 정적 파일을 서빙하기 위한 설정입니다.
 * SPA(Single Page Application) 라우팅도 지원합니다.
 */
@Configuration
class StaticResourceConfig {

    /**
     * 정적 리소스 라우터
     */
    @Bean
    fun staticResourceRouter(): RouterFunction<ServerResponse> {
        return nest(
            path("/static"),
            resources("/**", ClassPathResource("static/"))
        ).and(
            // HTML 페이지 라우팅
            route(GET("/")) { indexHandler() }
        ).and(
            route(GET("/login")) { loginHandler() }
        ).and(
            route(GET("/chat")) { chatHandler() }
        ).and(
            route(GET("/rooms")) { roomsHandler() }
        ).and(
            route(GET("/register")) { registerHandler() }
        ).and(
            // Chrome DevTools .well-known 경로 처리
            route(GET("/.well-known/**")) { wellKnownHandler() }
        ).and(
            // SPA 라우팅 (존재하지 않는 경로는 index.html로 리다이렉트)
            // API 경로(/api/*, /health, /ping, /info, /.well-known/*)는 제외
            route(GET("/{path:[^\\.]*}").and(path("/api/**").negate())
                .and(path("/health").negate())
                .and(path("/ping").negate())
                .and(path("/info").negate())
                .and(path("/.well-known/**").negate())) { spaHandler() }
        )
    }

    /**
     * 메인 페이지 핸들러
     */
    private fun indexHandler(): Mono<ServerResponse> {
        return ServerResponse.ok()
            .contentType(MediaType.TEXT_HTML)
            .bodyValue(getHtmlContent("index.html"))
    }

    /**
     * 로그인 페이지 핸들러
     */
    private fun loginHandler(): Mono<ServerResponse> {
        return ServerResponse.ok()
            .contentType(MediaType.TEXT_HTML)
            .bodyValue(getHtmlContent("login.html"))
    }

    /**
     * 채팅 페이지 핸들러
     */
    private fun chatHandler(): Mono<ServerResponse> {
        return ServerResponse.ok()
            .contentType(MediaType.TEXT_HTML)
            .bodyValue(getHtmlContent("chat.html"))
    }

    /**
     * 채팅방 목록 페이지 핸들러
     */
    private fun roomsHandler(): Mono<ServerResponse> {
        return ServerResponse.ok()
            .contentType(MediaType.TEXT_HTML)
            .bodyValue(getHtmlContent("rooms.html"))
    }

    /**
     * 회원가입 페이지 핸들러
     */
    private fun registerHandler(): Mono<ServerResponse> {
        return ServerResponse.ok()
            .contentType(MediaType.TEXT_HTML)
            .bodyValue(getHtmlContent("register.html"))
    }

    /**
     * SPA 라우팅 핸들러 (404 방지)
     */
    private fun spaHandler(): Mono<ServerResponse> {
        return ServerResponse.ok()
            .contentType(MediaType.TEXT_HTML)
            .bodyValue(getHtmlContent("index.html"))
    }

    /**
     * .well-known 경로 핸들러 (Chrome DevTools 등의 요청 처리)
     */
    private fun wellKnownHandler(): Mono<ServerResponse> {
        return ServerResponse.notFound().build()
    }

    /**
     * HTML 파일 내용 읽기
     */
    private fun getHtmlContent(fileName: String): String {
        return try {
            val resource = ClassPathResource("static/$fileName")
            if (resource.exists()) {
                resource.inputStream.bufferedReader().use { it.readText() }
            } else {
                createDefaultHtml(fileName)
            }
        } catch (e: Exception) {
            createDefaultHtml(fileName)
        }
    }

    /**
     * 기본 HTML 템플릿 생성 (파일이 없을 때)
     */
    private fun createDefaultHtml(fileName: String): String {
        val title = when (fileName) {
            "login.html" -> "로그인 - SimpleChatServer"
            "register.html" -> "회원가입 - SimpleChatServer"
            "chat.html" -> "채팅 - SimpleChatServer"
            "rooms.html" -> "채팅방 - SimpleChatServer"
            else -> "SimpleChatServer"
        }
        
        return """
            <!DOCTYPE html>
            <html lang="ko">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>$title</title>
                <link rel="stylesheet" href="/static/css/main.css">
            </head>
            <body>
                <div id="app">
                    <h1>$title</h1>
                    <p>페이지가 준비 중입니다...</p>
                </div>
                <script src="/static/js/main.js"></script>
            </body>
            </html>
        """.trimIndent()
    }
}