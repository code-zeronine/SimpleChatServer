-- SimpleChatServer Test Data
-- 개발 및 테스트용 초기 데이터

-- 기존 데이터 삭제 (개발환경용)
DELETE FROM users;

-- 비밀번호는 BCrypt로 해시된 "password123"
-- 실제 운영환경에서는 더 강력한 비밀번호를 사용해야 함
INSERT INTO users (email, password_hash, nickname, created_at) VALUES 
    ('admin@simplechat.com', '$2a$10$Xp/AegPdYzHqlN/Ny0haG.1KJP6D1azKQeP1Im2V6hz1dn4ZvU31S', 'Admin', CURRENT_TIMESTAMP),
    ('alice@example.com', '$2a$10$Xp/AegPdYzHqlN/Ny0haG.1KJP6D1azKQeP1Im2V6hz1dn4ZvU31S', 'Alice', CURRENT_TIMESTAMP - INTERVAL '1 day'),
    ('bob@example.com', '$2a$10$Xp/AegPdYzHqlN/Ny0haG.1KJP6D1azKQeP1Im2V6hz1dn4ZvU31S', 'Bob', CURRENT_TIMESTAMP - INTERVAL '2 day'),
    ('charlie@example.com', '$2a$10$Xp/AegPdYzHqlN/Ny0haG.1KJP6D1azKQeP1Im2V6hz1dn4ZvU31S', 'Charlie', CURRENT_TIMESTAMP - INTERVAL '3 day'),
    ('diana@example.com', '$2a$10$Xp/AegPdYzHqlN/Ny0haG.1KJP6D1azKQeP1Im2V6hz1dn4ZvU31S', 'Diana', CURRENT_TIMESTAMP - INTERVAL '4 day');

-- 추가 설명:
-- 모든 테스트 계정의 비밀번호는 "password123" 입니다.
-- BCrypt 해시: $2a$10$Xp/AegPdYzHqlN/Ny0haG.1KJP6D1azKQeP1Im2V6hz1dn4ZvU31S
-- 실제 애플리케이션에서는 사용자가 회원가입할 때 개별적으로 해시된 비밀번호가 저장됩니다.