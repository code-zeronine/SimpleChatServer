package com.simplechat.infrastructure.config

import com.simplechat.infrastructure.handler.ChatWebSocketHandler
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
     */
    @Bean
    fun webSocketHandlerAdapter(): WebSocketHandlerAdapter {
        return WebSocketHandlerAdapter()
    }
    
    /**
     * WebSocket URL 매핑을 구성합니다.
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