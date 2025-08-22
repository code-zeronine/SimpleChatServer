-- SimpleChatServer Database Schema
-- PostgreSQL용 사용자 테이블 및 채팅방 테이블 관련 스키마 정의

-- 기존 테이블이 있다면 삭제 (개발환경용)
DROP TABLE IF EXISTS chat_rooms CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- Users 테이블 생성
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(50) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 인덱스 생성 (성능 최적화)
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_nickname ON users(nickname);
CREATE INDEX IF NOT EXISTS idx_users_created_at ON users(created_at);

-- 제약조건 추가 설명을 위한 코멘트
COMMENT ON TABLE users IS '사용자 정보를 저장하는 테이블';
COMMENT ON COLUMN users.id IS '사용자 고유 식별자 (자동증가)';
COMMENT ON COLUMN users.email IS '사용자 이메일 주소 (로그인 ID로 사용, 중복 불허)';
COMMENT ON COLUMN users.password_hash IS '암호화된 비밀번호 해시';
COMMENT ON COLUMN users.nickname IS '사용자 닉네임 (채팅에서 표시되는 이름, 중복 불허)';
COMMENT ON COLUMN users.created_at IS '계정 생성 일시';

-- 이메일 형식 검증을 위한 CHECK 제약조건 추가
ALTER TABLE users ADD CONSTRAINT chk_users_email_format 
    CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$');

-- 닉네임 길이 제약조건 추가 (User 엔티티의 @Size 어노테이션과 일치)
ALTER TABLE users ADD CONSTRAINT chk_users_nickname_length 
    CHECK (LENGTH(nickname) >= 2 AND LENGTH(nickname) <= 50);

-- 비밀번호 해시 길이 제약조건 (최소한의 해시 길이 보장)
ALTER TABLE users ADD CONSTRAINT chk_users_password_hash_not_empty 
    CHECK (LENGTH(password_hash) > 0);

-- Chat Rooms 테이블 생성
CREATE TABLE chat_rooms (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_by BIGINT NOT NULL REFERENCES users(id),
    is_private BOOLEAN NOT NULL DEFAULT FALSE,
    max_participants INTEGER NOT NULL DEFAULT 100,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 채팅방 인덱스 생성 (성능 최적화)
CREATE INDEX IF NOT EXISTS idx_chat_rooms_name ON chat_rooms(name);
CREATE INDEX IF NOT EXISTS idx_chat_rooms_created_by ON chat_rooms(created_by);
CREATE INDEX IF NOT EXISTS idx_chat_rooms_is_private ON chat_rooms(is_private);
CREATE INDEX IF NOT EXISTS idx_chat_rooms_created_at ON chat_rooms(created_at);
CREATE INDEX IF NOT EXISTS idx_chat_rooms_updated_at ON chat_rooms(updated_at);

-- 채팅방 테이블 코멘트
COMMENT ON TABLE chat_rooms IS '채팅방 정보를 저장하는 테이블';
COMMENT ON COLUMN chat_rooms.id IS '채팅방 고유 식별자 (자동증가)';
COMMENT ON COLUMN chat_rooms.name IS '채팅방 이름 (1~100자)';
COMMENT ON COLUMN chat_rooms.description IS '채팅방 설명 (최대 500자)';
COMMENT ON COLUMN chat_rooms.created_by IS '채팅방 생성자 사용자 ID';
COMMENT ON COLUMN chat_rooms.is_private IS '비공개 채팅방 여부';
COMMENT ON COLUMN chat_rooms.max_participants IS '최대 참여자 수';
COMMENT ON COLUMN chat_rooms.created_at IS '채팅방 생성 일시';
COMMENT ON COLUMN chat_rooms.updated_at IS '채팅방 정보 수정 일시';

-- 채팅방 제약조건 추가
ALTER TABLE chat_rooms ADD CONSTRAINT chk_chat_rooms_name_length 
    CHECK (LENGTH(name) >= 1 AND LENGTH(name) <= 100);

ALTER TABLE chat_rooms ADD CONSTRAINT chk_chat_rooms_description_length 
    CHECK (description IS NULL OR LENGTH(description) <= 500);

ALTER TABLE chat_rooms ADD CONSTRAINT chk_chat_rooms_max_participants 
    CHECK (max_participants > 0 AND max_participants <= 1000);

-- 채팅방 이름에 대한 중복 제약조건 (선택사항)
-- CREATE UNIQUE INDEX IF NOT EXISTS idx_chat_rooms_name_unique ON chat_rooms(name);