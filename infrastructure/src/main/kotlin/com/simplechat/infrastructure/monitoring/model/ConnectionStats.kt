package com.simplechat.infrastructure.monitoring.model

/**
 * 연결 통계
 */
data class ConnectionStats(
    val activeConnections: Int,
    val totalRooms: Int,
    val totalUsers: Int,
    val averageSessionsPerUser: Double
)