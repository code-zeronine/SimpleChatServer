package com.simplechat.dto

import java.time.Instant

data class MessageDto(
    val id: String?,
    val roomId: String,
    val userId: Long,
    val content: String,
    val timestamp: Instant
)
