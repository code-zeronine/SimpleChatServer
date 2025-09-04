package com.simplechat.router

import com.simplechat.handler.HealthCheckHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router

@Configuration
class HealthCheckRouter(
    private val healthCheckHandler: HealthCheckHandler
) {

    @Bean
    fun healthCheckRoutes(): RouterFunction<ServerResponse> = router {
        GET("/health", healthCheckHandler::health)
        GET("/ping", healthCheckHandler::ping)
        GET("/info", healthCheckHandler::info)
    }
}