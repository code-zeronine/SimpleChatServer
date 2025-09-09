package com.simplechat.domain.exception

/**
 * 표준화된 오류 코드를 정의합니다.
 * 각 오류는 고유한 코드와 기본 메시지를 가집니다.
 */
enum class ErrorCode(val code: String, val message: String) {
    // General
    UNKNOWN_ERROR("GEN-001", "알 수 없는 오류가 발생했습니다."),
    INVALID_ARGUMENT("GEN-002", "유효하지 않은 인자입니다."),
    INVALID_STATE("GEN-003", "유효하지 않은 상태입니다."),

    // Authentication & Authorization
    AUTHENTICATION_FAILED("AUTH-001", "인증에 실패했습니다."),
    JWT_AUTHENTICATION_FAILED("AUTH-002", "JWT 인증에 실패했습니다."),
    ACCESS_DENIED("AUTH-003", "접근이 거부되었습니다."),
    INSUFFICIENT_PERMISSION("AUTH-004", "권한이 부족합니다."),

    // Resource
    RESOURCE_NOT_FOUND("RES-001", "리소스를 찾을 수 없습니다."),
    USER_NOT_FOUND("RES-002", "사용자를 찾을 수 없습니다."),
    CHAT_ROOM_NOT_FOUND("RES-003", "채팅방을 찾을 수 없습니다."),
    USER_CHAT_ROOM_NOT_FOUND("RES-004", "사용자-채팅방 관계를 찾을 수 없습니다."),

    // Business Logic
    BUSINESS_LOGIC_ERROR("BIZ-001", "비즈니스 로직 오류가 발생했습니다."),
    VALIDATION_FAILED("BIZ-002", "유효성 검사에 실패했습니다."),
    DUPLICATE_EMAIL("BIZ-003", "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME("BIZ-004", "이미 사용 중인 닉네임입니다."),
    CHAT_ROOM_FULL("BIZ-005", "채팅방이 가득 찼습니다."),
    USER_ALREADY_IN_CHAT_ROOM("BIZ-006", "사용자가 이미 채팅방에 참여하고 있습니다."),
    CANNOT_KICK_SELF("BIZ-007", "자기 자신을 추방할 수 없습니다."),
    CANNOT_CHANGE_OWN_ROLE("BIZ-008", "자신의 역할을 변경할 수 없습니다."),
    PRIVATE_ROOM_INVITE_ONLY("BIZ-009", "비공개 채팅방은 초대를 통해서만 참여할 수 있습니다."),

    // External Service
    EXTERNAL_SERVICE_ERROR("EXT-001", "외부 서비스 오류가 발생했습니다."),

    // Database
    DATABASE_ERROR("DB-001", "데이터베이스 오류가 발생했습니다."),
    DATABASE_CONNECTION_FAILED("DB-002", "데이터베이스 연결에 실패했습니다."),
    DATABASE_OPERATION_FAILED("DB-003", "데이터베이스 작업에 실패했습니다.");
}
