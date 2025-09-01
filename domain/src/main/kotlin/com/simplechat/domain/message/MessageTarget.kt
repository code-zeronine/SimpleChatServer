package com.simplechat.domain.message

/**
 * 메시지 전송 대상을 정의하는 sealed class (Domain)
 */
sealed class MessageTarget {
    /**
     * 특정 세션에게 전송
     */
    data class Session(val sessionId: String) : MessageTarget()
    
    /**
     * 특정 사용자에게 전송
     */
    data class User(val userId: Long) : MessageTarget()
    
    /**
     * 여러 사용자에게 전송
     */
    data class Users(val userIds: Set<Long>) : MessageTarget()
    
    /**
     * 특정 채팅방에 전송
     */
    data class Room(
        val chatRoomId: String,
        val excludeSessionId: String? = null
    ) : MessageTarget()
    
    /**
     * 전역 브로드캐스트
     */
    object Global : MessageTarget()
}