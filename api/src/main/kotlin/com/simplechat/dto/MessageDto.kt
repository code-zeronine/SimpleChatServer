package com.simplechat.dto

data class MessageDto(
    val id: String?,
    val roomId: String,
    val userId: Long,
    val userNickname: String?,
    val content: String,
    val timestamp: Long, // Unix timestamp in milliseconds
    val highlightedContent: String? = null,
    val messageType: String? = null
)
