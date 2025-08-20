package com.simplechat.handler

import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono
import java.time.Instant

@Component
class HealthCheckHandler {

    fun health(request: ServerRequest): Mono<ServerResponse> {
        val healthStatus = mapOf(
            "status" to "UP",
            "service" to "simple-chat-server",
            "timestamp" to Instant.now().toString(),
            "version" to "0.0.1-SNAPSHOT",
            "environment" to System.getProperty("spring.profiles.active", "dev")
        )

        return ServerResponse.ok()
            .bodyValue(healthStatus)
    }

    fun ping(request: ServerRequest): Mono<ServerResponse> {
        return ServerResponse.ok()
            .bodyValue(mapOf("message" to "pong", "timestamp" to Instant.now().toString()))
    }

    fun info(request: ServerRequest): Mono<ServerResponse> {
        val systemInfo = mapOf(
            "service" to "simple-chat-server",
            "version" to "0.0.1-SNAPSHOT",
            "java" to mapOf(
                "version" to System.getProperty("java.version"),
                "vendor" to System.getProperty("java.vendor")
            ),
            "system" to mapOf(
                "os" to System.getProperty("os.name"),
                "arch" to System.getProperty("os.arch"),
                "processors" to Runtime.getRuntime().availableProcessors()
            ),
            "memory" to mapOf(
                "total" to Runtime.getRuntime().totalMemory(),
                "free" to Runtime.getRuntime().freeMemory(),
                "max" to Runtime.getRuntime().maxMemory()
            ),
            "uptime" to System.currentTimeMillis(),
            "timestamp" to Instant.now().toString()
        )

        return ServerResponse.ok()
            .bodyValue(systemInfo)
    }
}