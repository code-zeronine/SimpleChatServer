-- SimpleChatServer Test Data
-- 개발 및 테스트용 초기 데이터

-- 기존 데이터 삭제 (개발환경용)
DELETE FROM users;

-- 비밀번호는 BCrypt로 해시된 "password123"
-- 실제 운영환경에서는 더 강력한 비밀번호를 사용해야 함
INSERT INTO users (email, password_hash, nickname, created_at) VALUES 
    ('admin@simplechat.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye3FZNGnFg3eq4DQJuPhm7g6XuJU.UXem', 'Admin', CURRENT_TIMESTAMP),
    ('alice@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye3FZNGnFg3eq4DQJuPhm7g6XuJU.UXem', 'Alice', CURRENT_TIMESTAMP - INTERVAL '1 day'),
    ('bob@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye3FZNGnFg3eq4DQJuPhm7g6XuJU.UXem', 'Bob', CURRENT_TIMESTAMP - INTERVAL '2 day'),
    ('charlie@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye3FZNGnFg3eq4DQJuPhm7g6XuJU.UXem', 'Charlie', CURRENT_TIMESTAMP - INTERVAL '3 day'),
    ('diana@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye3FZNGnFg3eq4DQJuPhm7g6XuJU.UXem', 'Diana', CURRENT_TIMESTAMP - INTERVAL '4 day');

-- 추가 설명:
-- 모든 테스트 계정의 비밀번호는 "password123" 입니다.
-- BCrypt 해시: $2a$10$N9qo8uLOickgx2ZMRZoMye3FZNGnFg3eq4DQJuPhm7g6XuJU.UXem
-- 실제 애플리케이션에서는 사용자가 회원가입할 때 개별적으로 해시된 비밀번호가 저장됩니다.