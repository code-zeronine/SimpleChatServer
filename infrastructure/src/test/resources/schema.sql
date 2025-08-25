-- SimpleChatServer Database Schema
-- PostgreSQL용 사용자 테이블 및 채팅방 테이블 관련 스키마 정의

-- 기존 테이블이 있다면 삭제 (개발환경용)
DROP TABLE IF EXISTS user_chat_rooms CASCADE;
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

-- User-Chat Room 관계 테이블 생성 (다대다 관계)
CREATE TABLE user_chat_rooms (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    chat_room_id BIGINT NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_read_at TIMESTAMP,
    is_muted BOOLEAN NOT NULL DEFAULT FALSE,
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    left_at TIMESTAMP,
    invited_by BIGINT REFERENCES users(id),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 복합 기본키 설정
    PRIMARY KEY (user_id, chat_room_id)
);

-- 사용자-채팅방 관계 테이블 인덱스 생성 (성능 최적화)
CREATE INDEX IF NOT EXISTS idx_user_chat_rooms_user_id ON user_chat_rooms(user_id);
CREATE INDEX IF NOT EXISTS idx_user_chat_rooms_chat_room_id ON user_chat_rooms(chat_room_id);
CREATE INDEX IF NOT EXISTS idx_user_chat_rooms_role ON user_chat_rooms(role);
CREATE INDEX IF NOT EXISTS idx_user_chat_rooms_is_active ON user_chat_rooms(is_active);
CREATE INDEX IF NOT EXISTS idx_user_chat_rooms_joined_at ON user_chat_rooms(joined_at);
CREATE INDEX IF NOT EXISTS idx_user_chat_rooms_last_read_at ON user_chat_rooms(last_read_at);
CREATE INDEX IF NOT EXISTS idx_user_chat_rooms_left_at ON user_chat_rooms(left_at);
CREATE INDEX IF NOT EXISTS idx_user_chat_rooms_invited_by ON user_chat_rooms(invited_by);

-- 활성 참여자만 조회하기 위한 복합 인덱스
CREATE INDEX IF NOT EXISTS idx_user_chat_rooms_active_participants 
    ON user_chat_rooms(chat_room_id, is_active) WHERE is_active = TRUE;

-- 사용자별 활성 채팅방 조회를 위한 복합 인덱스
CREATE INDEX IF NOT EXISTS idx_user_chat_rooms_user_active 
    ON user_chat_rooms(user_id, is_active) WHERE is_active = TRUE;

-- 사용자-채팅방 관계 테이블 코멘트
COMMENT ON TABLE user_chat_rooms IS '사용자와 채팅방 간의 다대다 관계를 저장하는 테이블';
COMMENT ON COLUMN user_chat_rooms.user_id IS '참여 사용자 ID';
COMMENT ON COLUMN user_chat_rooms.chat_room_id IS '참여 채팅방 ID';
COMMENT ON COLUMN user_chat_rooms.role IS '채팅방에서의 역할 (MEMBER, ADMIN, OWNER)';
COMMENT ON COLUMN user_chat_rooms.joined_at IS '채팅방 참여 일시';
COMMENT ON COLUMN user_chat_rooms.is_active IS '활성 참여 상태';
COMMENT ON COLUMN user_chat_rooms.last_read_at IS '마지막 읽음 시간';
COMMENT ON COLUMN user_chat_rooms.is_muted IS '채팅방 음소거 상태';
COMMENT ON COLUMN user_chat_rooms.is_pinned IS '채팅방 고정 상태';
COMMENT ON COLUMN user_chat_rooms.left_at IS '채팅방을 나간 시간';
COMMENT ON COLUMN user_chat_rooms.invited_by IS '초대한 사용자 ID';
COMMENT ON COLUMN user_chat_rooms.updated_at IS '마지막 업데이트 시간';

-- 사용자-채팅방 관계 제약조건 추가
ALTER TABLE user_chat_rooms ADD CONSTRAINT chk_user_chat_rooms_role 
    CHECK (role IN ('MEMBER', 'ADMIN', 'OWNER'));

ALTER TABLE user_chat_rooms ADD CONSTRAINT chk_user_chat_rooms_left_after_joined 
    CHECK (left_at IS NULL OR left_at >= joined_at);

ALTER TABLE user_chat_rooms ADD CONSTRAINT chk_user_chat_rooms_read_after_joined 
    CHECK (last_read_at IS NULL OR last_read_at >= joined_at);