package com.simplechat.infrastructure.service

import org.springframework.data.redis.listener.ChannelTopic
import java.time.Instant


/**
 * 채널 정보 데이터 클래스
 */
data class ChannelInfo(
    val name: String,
    val createdAt: Instant,
    val lastUsed: Instant,
    val topic: ChannelTopic,
    val shouldCleanup: Boolean = false
)