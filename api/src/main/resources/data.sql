-- SimpleChatServer PostgreSQL Database Initial Data
-- 테스트 및 개발용 초기 데이터

-- 기존 데이터 삭제 (개발환경용)
DELETE FROM user_chat_rooms;
DELETE FROM chat_rooms;
DELETE FROM users;

-- ============================================================
-- 테스트용 사용자 데이터
-- ============================================================

-- 비밀번호는 BCrypt로 해시된 "password123"
-- 실제 운영환경에서는 더 강력한 비밀번호를 사용해야 함
INSERT INTO users (email, password_hash, nickname) VALUES 
('admin@simplechat.com', '$2a$10$Xp/AegPdYzHqlN/Ny0haG.1KJP6D1azKQeP1Im2V6hz1dn4ZvU31S', 'Admin'),
('alice@simplechat.com', '$2a$10$Xp/AegPdYzHqlN/Ny0haG.1KJP6D1azKQeP1Im2V6hz1dn4ZvU31S', 'Alice'),
('bob@simplechat.com', '$2a$10$Xp/AegPdYzHqlN/Ny0haG.1KJP6D1azKQeP1Im2V6hz1dn4ZvU31S', 'Bob'),
('charlie@simplechat.com', '$2a$10$Xp/AegPdYzHqlN/Ny0haG.1KJP6D1azKQeP1Im2V6hz1dn4ZvU31S', 'Charlie'),
('diana@simplechat.com', '$2a$10$Xp/AegPdYzHqlN/Ny0haG.1KJP6D1azKQeP1Im2V6hz1dn4ZvU31S', 'Diana'),
('eve@simplechat.com', '$2a$10$Xp/AegPdYzHqlN/Ny0haG.1KJP6D1azKQeP1Im2V6hz1dn4ZvU31S', 'Eve');

-- ============================================================
-- 테스트용 채팅방 데이터
-- ============================================================

INSERT INTO chat_rooms (name, description, created_by, is_private) VALUES 
('일반 채팅방', '누구나 참여할 수 있는 공개 채팅방입니다.', 1, FALSE),
('개발자 모임', '개발 관련 토론을 위한 채팅방입니다.', 1, FALSE),
('비밀 채팅방', '초대받은 사람만 참여할 수 있습니다.', 1, TRUE),
('취미 공유방', '취미를 공유하고 이야기하는 공간입니다.', 2, FALSE),
('스터디 그룹', '함께 공부하는 스터디 그룹입니다.', 3, FALSE),
('프로젝트 논의', '프로젝트 진행 상황을 논의합니다.', 1, TRUE),
('자유 수다방', '자유롭게 대화하는 공간입니다.', 4, FALSE),
('독서 모임', '책에 대해 이야기하는 모임입니다.', 5, FALSE);

-- ============================================================
-- 테스트용 사용자-채팅방 관계 데이터
-- ============================================================

INSERT INTO user_chat_rooms (user_id, chat_room_id, role) VALUES 
-- Admin 사용자 (모든 방의 소유자 또는 관리자)
(1, 1, 'OWNER'),
(1, 2, 'OWNER'),
(1, 3, 'OWNER'),
(1, 6, 'OWNER'),

-- Alice 사용자
(2, 1, 'MEMBER'),
(2, 2, 'MEMBER'),
(2, 4, 'OWNER'),
(2, 5, 'ADMIN'),
(2, 7, 'MEMBER'),

-- Bob 사용자
(3, 1, 'MEMBER'),
(3, 2, 'ADMIN'),
(3, 5, 'OWNER'),
(3, 7, 'MEMBER'),
(3, 8, 'MEMBER'),

-- Charlie 사용자
(4, 1, 'MEMBER'),
(4, 4, 'MEMBER'),
(4, 5, 'MEMBER'),
(4, 7, 'OWNER'),
(4, 8, 'ADMIN'),

-- Diana 사용자
(5, 1, 'MEMBER'),
(5, 2, 'MEMBER'),
(5, 4, 'MEMBER'),
(5, 8, 'OWNER'),

-- Eve 사용자
(6, 2, 'MEMBER'),
(6, 5, 'MEMBER'),
(6, 6, 'MEMBER'),
(6, 7, 'MEMBER'),
(6, 8, 'MEMBER');

-- ============================================================
-- 추가 설명
-- ============================================================

-- 모든 테스트 계정의 비밀번호는 "password123" 입니다.
-- BCrypt 해시: $2a$10$Xp/AegPdYzHqlN/Ny0haG.1KJP6D1azKQeP1Im2V6hz1dn4ZvU31S
-- 실제 애플리케이션에서는 사용자가 회원가입할 때 개별적으로 해시된 비밀번호가 저장됩니다.

-- 채팅방 구성:
-- 1. 일반 채팅방 (공개) - 5명 참여
-- 2. 개발자 모임 (공개) - 4명 참여  
-- 3. 비밀 채팅방 (비공개) - 1명 (Admin만)
-- 4. 취미 공유방 (공개) - 3명 참여
-- 5. 스터디 그룹 (공개) - 4명 참여
-- 6. 프로젝트 논의 (비공개) - 2명 참여
-- 7. 자유 수다방 (공개) - 4명 참여
-- 8. 독서 모임 (공개) - 4명 참여