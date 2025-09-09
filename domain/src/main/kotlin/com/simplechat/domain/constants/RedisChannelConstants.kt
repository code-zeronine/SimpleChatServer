package com.simplechat.domain.constants

/**
 * Redis 채널 관련 상수 정의
 * 
 * 도메인 레이어에서 정의하여 모든 레이어에서 공통으로 사용할 수 있도록 합니다.
 * 채널 네이밍 규칙과 패턴을 중앙에서 관리합니다.
 */
object RedisChannelConstants {
    
    // 채널 접두사 정의
    const val CHAT_ROOM_PREFIX = "chat:room:"
    const val USER_PRIVATE_PREFIX = "user:private:"
    const val GLOBAL_CHANNEL = "chat:global"
    const val SYSTEM_CHANNEL = "system:notifications"
    const val ADMIN_CHANNEL = "admin:control"
    
    // 검증을 위한 제약 사항
    const val MAX_CHANNEL_NAME_LENGTH = 100
    const val MIN_ROOM_ID = 1L
    const val MAX_ROOM_ID = 999999999L
    const val MIN_USER_ID = 1L
    const val MAX_USER_ID = 999999999L
    
    /**
     * 채팅방 채널명을 생성합니다.
     * 
     * @param roomId 채팅방 ID
     * @return 채널명 (예: "chat:room:123")
     */
    fun createRoomChannelName(roomId: Long): String {
        validateRoomId(roomId)
        return "$CHAT_ROOM_PREFIX$roomId"
    }
    
    /**
     * 사용자 개인 채널명을 생성합니다.
     * 
     * @param userId 사용자 ID
     * @return 채널명 (예: "user:private:456")
     */
    fun createUserPrivateChannelName(userId: Long): String {
        validateUserId(userId)
        return "$USER_PRIVATE_PREFIX$userId"
    }
    
    /**
     * 채널명에서 채팅방 ID를 추출합니다.
     * 
     * @param channelName 채널명
     * @return 채팅방 ID (추출 실패시 null)
     */
    fun extractRoomIdFromChannel(channelName: String): Long? {
        return try {
            if (channelName.startsWith(CHAT_ROOM_PREFIX)) {
                val roomId = channelName.substring(CHAT_ROOM_PREFIX.length).toLong()
                if (isValidRoomId(roomId)) roomId else null
            } else {
                null
            }
        } catch (e: NumberFormatException) {
            null
        }
    }
    
    /**
     * 채널명에서 사용자 ID를 추출합니다.
     * 
     * @param channelName 채널명
     * @return 사용자 ID (추출 실패시 null)
     */
    fun extractUserIdFromChannel(channelName: String): Long? {
        return try {
            if (channelName.startsWith(USER_PRIVATE_PREFIX)) {
                val userId = channelName.substring(USER_PRIVATE_PREFIX.length).toLong()
                if (isValidUserId(userId)) userId else null
            } else {
                null
            }
        } catch (e: NumberFormatException) {
            null
        }
    }
    
    /**
     * 채널 타입을 식별합니다.
     * 
     * @param channelName 채널명
     * @return 채널 타입
     */
    fun identifyChannelType(channelName: String): ChannelType {
        return when {
            channelName == GLOBAL_CHANNEL -> ChannelType.GLOBAL
            channelName == SYSTEM_CHANNEL -> ChannelType.SYSTEM
            channelName == ADMIN_CHANNEL -> ChannelType.ADMIN
            channelName.startsWith(CHAT_ROOM_PREFIX) -> ChannelType.CHAT_ROOM
            channelName.startsWith(USER_PRIVATE_PREFIX) -> ChannelType.USER_PRIVATE
            else -> ChannelType.CUSTOM
        }
    }
    
    /**
     * 채널명이 유효한지 검증합니다.
     * 
     * @param channelName 채널명
     * @return 검증 결과
     */
    fun isValidChannelName(channelName: String): Boolean {
        if (channelName.isBlank() || channelName.length > MAX_CHANNEL_NAME_LENGTH) {
            return false
        }
        
        // 허용된 문자만 포함하는지 확인 (영문, 숫자, 콜론, 점, 밑줄, 하이픈)
        val validPattern = Regex("^[a-zA-Z0-9:._-]+$")
        return validPattern.matches(channelName)
    }
    
    /**
     * 채팅방 ID 유효성 검사
     */
    private fun validateRoomId(roomId: Long) {
        if (!isValidRoomId(roomId)) {
            throw IllegalArgumentException("Invalid room ID: $roomId (must be between $MIN_ROOM_ID and $MAX_ROOM_ID)")
        }
    }
    
    /**
     * 사용자 ID 유효성 검사
     */
    private fun validateUserId(userId: Long) {
        if (!isValidUserId(userId)) {
            throw IllegalArgumentException("Invalid user ID: $userId (must be between $MIN_USER_ID and $MAX_USER_ID)")
        }
    }
    
    /**
     * 채팅방 ID 유효성 확인
     */
    private fun isValidRoomId(roomId: Long): Boolean {
        return roomId in MIN_ROOM_ID..MAX_ROOM_ID
    }
    
    /**
     * 사용자 ID 유효성 확인
     */
    private fun isValidUserId(userId: Long): Boolean {
        return userId in MIN_USER_ID..MAX_USER_ID
    }
}
