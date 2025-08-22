-- SimpleChatServer Test Data
-- 개발 및 테스트용 초기 데이터

-- 기존 데이터 삭제 (개발환경용)
DELETE FROM chat_rooms;
DELETE FROM users;

-- 비밀번호는 BCrypt로 해시된 "password123"
-- 실제 운영환경에서는 더 강력한 비밀번호를 사용해야 함
INSERT INTO users (email, password_hash, nickname, created_at) VALUES 
    ('admin@simplechat.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye3FZNGnFg3eq4DQJuPhm7g6XuJU.UXem', 'Admin', CURRENT_TIMESTAMP),
    ('alice@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye3FZNGnFg3eq4DQJuPhm7g6XuJU.UXem', 'Alice', CURRENT_TIMESTAMP - INTERVAL '1 day'),
    ('bob@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye3FZNGnFg3eq4DQJuPhm7g6XuJU.UXem', 'Bob', CURRENT_TIMESTAMP - INTERVAL '2 day'),
    ('charlie@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye3FZNGnFg3eq4DQJuPhm7g6XuJU.UXem', 'Charlie', CURRENT_TIMESTAMP - INTERVAL '3 day'),
    ('diana@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye3FZNGnFg3eq4DQJuPhm7g6XuJU.UXem', 'Diana', CURRENT_TIMESTAMP - INTERVAL '4 day');

-- 채팅방 테스트 데이터 삽입
INSERT INTO chat_rooms (name, description, created_by, is_private, max_participants, created_at, updated_at) VALUES 
    ('General Chat', '일반 대화를 위한 공개 채팅방', 1, FALSE, 100, CURRENT_TIMESTAMP - INTERVAL '1 hour', CURRENT_TIMESTAMP - INTERVAL '30 minutes'),
    ('Tech Talk', '기술 관련 토론 채팅방', 2, FALSE, 50, CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP - INTERVAL '1 hour'),
    ('Private Discussion', 'Alice와 Bob의 비공개 채팅방', 2, TRUE, 10, CURRENT_TIMESTAMP - INTERVAL '3 hours', CURRENT_TIMESTAMP - INTERVAL '2 hours'),
    ('Team Project', '프로젝트 팀 채팅방', 3, FALSE, 20, CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP - INTERVAL '12 hours'),
    ('Random Chat', '자유 주제 채팅방', 4, FALSE, 200, CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '1 day');

-- 추가 설명:
-- 모든 테스트 계정의 비밀번호는 "password123" 입니다.
-- BCrypt 해시: $2a$10$N9qo8uLOickgx2ZMRZoMye3FZNGnFg3eq4DQJuPhm7g6XuJU.UXem
-- 실제 애플리케이션에서는 사용자가 회원가입할 때 개별적으로 해시된 비밀번호가 저장됩니다.
-- 채팅방은 각각 다른 사용자가 생성하였으며, 공개/비공개 설정과 다양한 참여자 수 제한이 적용되어 있습니다.