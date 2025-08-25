package com.simplechat.domain.entity

/**
 * 채팅방에서 사용자의 역할을 정의하는 열거형
 * 
 * 채팅방 내에서 사용자가 가질 수 있는 권한과 역할을 나타냅니다.
 */
enum class ChatRoomRole(
    val displayName: String,
    val description: String,
    val level: Int // 권한 레벨 (높을수록 더 많은 권한)
) {
    /**
     * 일반 멤버 - 기본 채팅 권한만 보유
     */
    MEMBER("멤버", "일반 채팅 참여자", 1),
    
    /**
     * 관리자 - 채팅방 관리 권한 보유 (멤버 관리, 설정 변경 등)
     */
    ADMIN("관리자", "채팅방 관리 권한 보유", 2),
    
    /**
     * 소유자 - 모든 권한 보유 (방 삭제, 관리자 지정 등)
     */
    OWNER("소유자", "채팅방 소유자 (모든 권한)", 3);

    /**
     * 현재 역할이 지정된 역할보다 높은 권한을 가지는지 확인합니다.
     */
    fun hasHigherAuthorityThan(other: ChatRoomRole): Boolean {
        return this.level > other.level
    }
    
    /**
     * 현재 역할이 지정된 역할과 같거나 높은 권한을 가지는지 확인합니다.
     */
    fun hasAuthorityEqualOrHigherThan(other: ChatRoomRole): Boolean {
        return this.level >= other.level
    }
    
    /**
     * 관리자 권한 이상인지 확인합니다.
     */
    fun isAdminOrAbove(): Boolean {
        return this.level >= ADMIN.level
    }
    
    /**
     * 소유자 권한인지 확인합니다.
     */
    fun isOwner(): Boolean {
        return this == OWNER
    }
    
    /**
     * 일반 멤버 권한인지 확인합니다.
     */
    fun isMember(): Boolean {
        return this == MEMBER
    }
    
    companion object {
        /**
         * 문자열로부터 ChatRoomRole을 찾습니다.
         */
        fun fromString(value: String?): ChatRoomRole? {
            return values().find { it.name.equals(value, ignoreCase = true) }
        }
        
        /**
         * 레벨로부터 ChatRoomRole을 찾습니다.
         */
        fun fromLevel(level: Int): ChatRoomRole? {
            return values().find { it.level == level }
        }
        
        /**
         * 기본 역할 (신규 가입자)
         */
        fun defaultRole(): ChatRoomRole = MEMBER
        
        /**
         * 모든 역할을 권한 레벨 순으로 정렬하여 반환합니다.
         */
        fun getAllRolesByLevel(): List<ChatRoomRole> {
            return values().sortedBy { it.level }
        }
    }
}