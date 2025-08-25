-- SimpleChatServer Test Data
-- 개발 및 테스트용 초기 데이터

-- 기존 데이터 삭제 (개발환경용)
DELETE FROM user_chat_rooms;
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

-- 사용자-채팅방 관계 테스트 데이터 삽입
INSERT INTO user_chat_rooms (user_id, chat_room_id, role, joined_at, is_active, last_read_at, is_muted, is_pinned, left_at, invited_by, updated_at) VALUES 
    -- General Chat 참여자들
    (1, 1, 'OWNER', CURRENT_TIMESTAMP - INTERVAL '1 hour', TRUE, CURRENT_TIMESTAMP - INTERVAL '10 minutes', FALSE, TRUE, NULL, NULL, CURRENT_TIMESTAMP - INTERVAL '10 minutes'),
    (2, 1, 'ADMIN', CURRENT_TIMESTAMP - INTERVAL '50 minutes', TRUE, CURRENT_TIMESTAMP - INTERVAL '5 minutes', FALSE, FALSE, NULL, 1, CURRENT_TIMESTAMP - INTERVAL '5 minutes'),
    (3, 1, 'MEMBER', CURRENT_TIMESTAMP - INTERVAL '45 minutes', TRUE, CURRENT_TIMESTAMP - INTERVAL '15 minutes', FALSE, FALSE, NULL, 1, CURRENT_TIMESTAMP - INTERVAL '15 minutes'),
    (4, 1, 'MEMBER', CURRENT_TIMESTAMP - INTERVAL '40 minutes', TRUE, CURRENT_TIMESTAMP - INTERVAL '20 minutes', TRUE, FALSE, NULL, 2, CURRENT_TIMESTAMP - INTERVAL '20 minutes'),
    
    -- Tech Talk 참여자들  
    (2, 2, 'OWNER', CURRENT_TIMESTAMP - INTERVAL '2 hours', TRUE, CURRENT_TIMESTAMP - INTERVAL '30 minutes', FALSE, TRUE, NULL, NULL, CURRENT_TIMESTAMP - INTERVAL '30 minutes'),
    (1, 2, 'ADMIN', CURRENT_TIMESTAMP - INTERVAL '90 minutes', TRUE, CURRENT_TIMESTAMP - INTERVAL '25 minutes', FALSE, FALSE, NULL, 2, CURRENT_TIMESTAMP - INTERVAL '25 minutes'),
    (5, 2, 'MEMBER', CURRENT_TIMESTAMP - INTERVAL '80 minutes', TRUE, CURRENT_TIMESTAMP - INTERVAL '35 minutes', FALSE, FALSE, NULL, 2, CURRENT_TIMESTAMP - INTERVAL '35 minutes'),
    
    -- Private Discussion 참여자들 (비공개방)
    (2, 3, 'OWNER', CURRENT_TIMESTAMP - INTERVAL '3 hours', TRUE, CURRENT_TIMESTAMP - INTERVAL '1 hour', FALSE, TRUE, NULL, NULL, CURRENT_TIMESTAMP - INTERVAL '1 hour'),
    (3, 3, 'MEMBER', CURRENT_TIMESTAMP - INTERVAL '2.5 hours', TRUE, CURRENT_TIMESTAMP - INTERVAL '1.5 hours', FALSE, TRUE, NULL, 2, CURRENT_TIMESTAMP - INTERVAL '1.5 hours'),
    
    -- Team Project 참여자들
    (3, 4, 'OWNER', CURRENT_TIMESTAMP - INTERVAL '1 day', TRUE, CURRENT_TIMESTAMP - INTERVAL '2 hours', FALSE, TRUE, NULL, NULL, CURRENT_TIMESTAMP - INTERVAL '2 hours'),
    (1, 4, 'ADMIN', CURRENT_TIMESTAMP - INTERVAL '20 hours', TRUE, CURRENT_TIMESTAMP - INTERVAL '3 hours', FALSE, FALSE, NULL, 3, CURRENT_TIMESTAMP - INTERVAL '3 hours'),
    (4, 4, 'MEMBER', CURRENT_TIMESTAMP - INTERVAL '18 hours', TRUE, CURRENT_TIMESTAMP - INTERVAL '4 hours', FALSE, FALSE, NULL, 3, CURRENT_TIMESTAMP - INTERVAL '4 hours'),
    (5, 4, 'MEMBER', CURRENT_TIMESTAMP - INTERVAL '16 hours', FALSE, CURRENT_TIMESTAMP - INTERVAL '6 hours', FALSE, FALSE, CURRENT_TIMESTAMP - INTERVAL '8 hours', 1, CURRENT_TIMESTAMP - INTERVAL '8 hours'),
    
    -- Random Chat 참여자들
    (4, 5, 'OWNER', CURRENT_TIMESTAMP - INTERVAL '2 days', TRUE, CURRENT_TIMESTAMP - INTERVAL '1 day', FALSE, FALSE, NULL, NULL, CURRENT_TIMESTAMP - INTERVAL '1 day'),
    (1, 5, 'MEMBER', CURRENT_TIMESTAMP - INTERVAL '1.8 days', TRUE, CURRENT_TIMESTAMP - INTERVAL '1.2 days', TRUE, FALSE, NULL, 4, CURRENT_TIMESTAMP - INTERVAL '1.2 days'),
    (5, 5, 'MEMBER', CURRENT_TIMESTAMP - INTERVAL '1.5 days', TRUE, CURRENT_TIMESTAMP - INTERVAL '1.1 days', FALSE, FALSE, NULL, 4, CURRENT_TIMESTAMP - INTERVAL '1.1 days');

-- 추가 설명:
-- 사용자-채팅방 관계 테이블에는 다음과 같은 시나리오가 포함되어 있습니다:
-- - 각 채팅방마다 소유자(OWNER), 관리자(ADMIN), 멤버(MEMBER) 역할 배정
-- - 일부 사용자는 음소거(is_muted) 또는 고정(is_pinned) 상태
-- - 한 명의 사용자(사용자 5)는 Team Project 방을 나간 상태(left_at 설정)
-- - 초대자 정보(invited_by) 추적
-- - 마지막 읽음 시간(last_read_at) 다양화로 읽지 않은 메시지 시뮬레이션