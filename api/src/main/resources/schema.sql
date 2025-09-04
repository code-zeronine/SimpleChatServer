-- SimpleChatServer PostgreSQL Database Schema
-- 기존 테이블 삭제 (외래 키 종속성 순서 고려)

-- 1. 연결 테이블 먼저 삭제
DROP TABLE IF EXISTS user_chat_rooms CASCADE;

-- 2. 종속 테이블 삭제
DROP TABLE IF EXISTS chat_rooms CASCADE;

-- 3. 기본 테이블 삭제
DROP TABLE IF EXISTS users CASCADE;

-- ============================================================
-- 테이블 생성
-- ============================================================

-- 사용자 테이블
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 채팅방 테이블
CREATE TABLE chat_rooms (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_by BIGINT NOT NULL,
    is_private BOOLEAN NOT NULL DEFAULT FALSE,
    max_participants INTEGER NOT NULL DEFAULT 100,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_rooms_created_by FOREIGN KEY (created_by) REFERENCES users(id)
);

-- 사용자-채팅방 관계 테이블
CREATE TABLE user_chat_rooms (
    user_id BIGINT NOT NULL,
    chat_room_id BIGINT NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_read_at TIMESTAMP,
    is_muted BOOLEAN NOT NULL DEFAULT FALSE,
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    left_at TIMESTAMP,
    invited_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, chat_room_id),
    CONSTRAINT fk_user_chat_rooms_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_user_chat_rooms_chat_room_id FOREIGN KEY (chat_room_id) REFERENCES chat_rooms(id),
    CONSTRAINT fk_user_chat_rooms_invited_by FOREIGN KEY (invited_by) REFERENCES users(id)
);

-- ============================================================
-- 인덱스 생성
-- ============================================================

-- users 테이블 인덱스
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_nickname ON users(nickname);
CREATE INDEX idx_users_created_at ON users(created_at);

-- chat_rooms 테이블 인덱스
CREATE INDEX idx_chat_rooms_created_by ON chat_rooms(created_by);
CREATE INDEX idx_chat_rooms_name ON chat_rooms(name);
CREATE INDEX idx_chat_rooms_is_private ON chat_rooms(is_private);
CREATE INDEX idx_chat_rooms_created_at ON chat_rooms(created_at);

-- user_chat_rooms 테이블 인덱스
CREATE INDEX idx_user_chat_rooms_user_id ON user_chat_rooms(user_id);
CREATE INDEX idx_user_chat_rooms_chat_room_id ON user_chat_rooms(chat_room_id);
CREATE INDEX idx_user_chat_rooms_is_active ON user_chat_rooms(is_active);
CREATE INDEX idx_user_chat_rooms_joined_at ON user_chat_rooms(joined_at);
CREATE INDEX idx_user_chat_rooms_role ON user_chat_rooms(role);

-- 복합 인덱스
CREATE INDEX idx_user_chat_rooms_user_active ON user_chat_rooms(user_id, is_active);
CREATE INDEX idx_user_chat_rooms_room_active ON user_chat_rooms(chat_room_id, is_active);

-- ============================================================
-- 제약 조건 및 체크
-- ============================================================

-- role 값 제약
ALTER TABLE user_chat_rooms ADD CONSTRAINT chk_user_chat_rooms_role 
    CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER'));

-- max_participants 최소값 체크
ALTER TABLE chat_rooms ADD CONSTRAINT chk_chat_rooms_max_participants 
    CHECK (max_participants > 0);

-- 이메일 형식 체크 (간단한 형식)
ALTER TABLE users ADD CONSTRAINT chk_users_email_format 
    CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$');

-- 닉네임 길이 체크
ALTER TABLE users ADD CONSTRAINT chk_users_nickname_length 
    CHECK (LENGTH(nickname) >= 2 AND LENGTH(nickname) <= 100);

-- 채팅방 이름 길이 체크
ALTER TABLE chat_rooms ADD CONSTRAINT chk_chat_rooms_name_length 
    CHECK (LENGTH(name) >= 1 AND LENGTH(name) <= 255);

-- ============================================================
-- 참고: updated_at 필드는 애플리케이션 레벨에서 관리됩니다
-- R2DBC는 PostgreSQL 트리거 함수의 달러 인용 구문을 파싱하지 못하므로
-- Spring Data R2DBC의 @LastModifiedDate 어노테이션을 사용하여 처리합니다
-- ============================================================
