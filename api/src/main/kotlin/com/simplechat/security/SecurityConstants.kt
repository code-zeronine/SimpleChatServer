package com.simplechat.security

object SecurityConstants {
    const val JWT_HEADER = "Authorization"
    const val JWT_PREFIX = "Bearer "
    const val JWT_EXPIRATION_TIME = 86400000L // 24 hours in milliseconds
    
    // Public endpoints that don't require authentication
    val PUBLIC_ENDPOINTS = arrayOf(
        "/api/health",
        "/api/ping", 
        "/api/info",
        "/api/auth/login",
        "/api/auth/register",
        "/actuator/health"
    )
    
    // WebSocket endpoints
    const val WEBSOCKET_ENDPOINT = "/ws"
    
    // CORS configuration
    val ALLOWED_ORIGINS = arrayOf(
        "http://localhost:3000",
        "http://localhost:8080", 
        "http://localhost:8081"
    )
    
    val ALLOWED_METHODS = arrayOf(
        "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
    )
    
    val ALLOWED_HEADERS = arrayOf(
        "Authorization",
        "Content-Type",
        "Accept",
        "Origin",
        "Access-Control-Request-Method",
        "Access-Control-Request-Headers"
    )
}