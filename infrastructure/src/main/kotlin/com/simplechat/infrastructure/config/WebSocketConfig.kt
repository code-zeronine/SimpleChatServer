package com.simplechat.infrastructure.config

import com.simplechat.infrastructure.handler.ChatWebSocketHandler
import com.simplechat.infrastructure.security.WebSocketAuthService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter

/**
 * WebSocket 설정
 * 
 * WebSocket 엔드포인트와 핸들러를 구성합니다.
 */
@Configuration
class WebSocketConfig {
    
    /**
     * WebSocket 핸들러 어댑터를 구성합니다.
     * JWT 인증을 처리하는 커스텀 WebSocketAuthService를 사용합니다.
     */
    @Bean
    fun webSocketHandlerAdapter(webSocketAuthService: WebSocketAuthService): WebSocketHandlerAdapter {
        return WebSocketHandlerAdapter(webSocketAuthService)
    }
    
    /**
     * WebSocket URL 매핑를 구성합니다.
     */
    @Bean
    fun webSocketHandlerMapping(
        chatWebSocketHandler: ChatWebSocketHandler
    ): HandlerMapping {
        val map = mapOf<String, WebSocketHandler>(
            "/ws/chat/**" to chatWebSocketHandler
        )
        
        val handlerMapping = SimpleUrlHandlerMapping()
        handlerMapping.urlMap = map
        handlerMapping.order = 1
        
        return handlerMapping
    }
}