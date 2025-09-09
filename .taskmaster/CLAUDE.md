# Task Master Commands and Task Overview (Korean)

아래 안내와 작업 목록은 이 저장소의 Task Master 설정(.taskmaster/config.json, state.json, docs/prd.txt, reports/*)을 기반으로 합니다. CLAUDE.md에서 이 파일을 import하여, 대화형 에이전트에서 바로 확인/활용할 수 있습니다.

## 어떻게 사용하나요?
- 전역 설치(선택): `npm install -g task-master-ai`
- 빠른 확인: `task-master --version`
- PRD 파싱 예시: `task-master parse-prd ./.taskmaster/docs/prd.txt -n 10 --lang ko`
- 모델 상태 확인: `task-master models --status`

자세한 설치 가이드는 `.claude/commands/tm/setup/*` 문서를 참고하세요.

## 현재 프로젝트 구현 상태 (2025-09-09 기준)

### 완료된 주요 기능
1. **Spring Boot WebFlux + Kotlin Coroutines 기반 아키텍처**
   - 멀티모듈 구조 (api, domain, infrastructure)
   - Gradle 8.14, Kotlin 2.0.21
   - WebFlux 리액티브 프로그래밍 패턴

2. **사용자 인증 시스템**
   - JWT 기반 인증 (JwtAuthenticationHelper)
   - PostgreSQL R2DBC 연동
   - 회원가입/로그인 API 및 UI
   - 토큰 검증 및 갱신

3. **채팅방 관리 시스템**
   - 채팅방 CRUD API (ChatRoomController)
   - 참여자 관리 (참여/퇴장/역할 변경)
   - 공개/비공개 채팅방 지원
   - 현대적 UI (glassmorphism 디자인)

4. **실시간 메시징**
   - WebSocket 기반 실시간 통신
   - 메시지 송수신 및 입력 상태 표시
   - MongoDB 기반 메시지 저장
   - 온라인/오프라인 상태 관리

5. **프론트엔드 UI**
   - 반응형 모던 디자인
   - 접근성 (ARIA) 지원
   - 실시간 알림 (토스트)
   - 애니메이션 및 로딩 상태

### 진행 중인 작업
1. **고급 메시지 기능**
   - 파일 첨부 (UI 구현 완료, 백엔드 연동 필요)
   - 메시지 검색 및 필터링
   - 메시지 삭제/수정

2. **성능 최적화**
   - 메시지 페이지네이션 개선
   - 캐싱 전략 (Redis 활용)
   - 대용량 채팅방 처리

### 남은 작업 목록
1. **파일 업로드 시스템** (복잡도: 6)
2. **1:1 DM 기능** (복잡도: 5)
3. **관리자 기능 확장** (복잡도: 7)
4. **모니터링 및 분석** (복잡도: 8)
5. **테스트 커버리지 확대** (복잡도: 6)
6. **Docker 및 배포 자동화** (복잡도: 7)

### 프로젝트 현황
- **Kotlin 파일**: 167개
- **프론트엔드 파일**: 33개 (HTML/CSS/JS)
- **테스트 파일**: 18개
- **컨트롤러**: 6개 (Auth, ChatRoom, Message, User, WebSocket 관련)
- **데이터베이스**: PostgreSQL (사용자/채팅방), MongoDB (메시지)

## 개발 우선순위 및 로드맵

### Phase 1: MVP 완성 (거의 완료)
- **Foundation Setup**: Spring Boot WebFlux + 멀티모듈 구조
- **User Authentication**: JWT 기반 인증 시스템
- **Chat Room Management**: 채팅방 CRUD 및 참여자 관리
- **Real-time Messaging**: WebSocket 기반 메시징
- **Message Persistence**: MongoDB 메시지 저장
- **Modern UI/UX**: 반응형 현대적 디자인

### Phase 2: 고급 기능 (다음 단계)
- **파일 업로드**: 이미지, 문서 첨부 시스템
- **1:1 DM**: 개인 메시지 기능
- **메시지 검색**: 전문 검색 및 필터링
- **모바일 최적화**: PWA 및 푸시 알림
- **알림 시스템**: 이메일/SMS 알림

### Phase 3: 확장성 및 운영 (미래)
- **분석 대시보드**: 사용자 활동 분석
- **보안 강화**: 레이트 리미팅, 스패머 차단
- **성능 최적화**: 클러스터링, 로드 밸런싱
- **DevOps**: CI/CD 파이프라인, 모니터링
- **국제화**: 다국어 지원

## 최근 구현된 주요 기능들

### UI/UX 개선
- **Glassmorphism 디자인**: 현대적인 반투명 효과
- **애니메이션**: 부드러운 카드 로딩 및 전환 효과
- **접근성**: ARIA 레이블 및 키보드 내비게이션
- **반응형**: 모바일/태블릿/데스크톱 최적화

### 실시간 기능
- **온라인 상태**: 사용자 접속 상태 실시간 표시
- **입력 표시**: "typing..." 상태 표시
- **즉시 알림**: 메시지 도착 시 토스트 알림
- **참여자 관리**: 실시간 입장/퇴장 처리

### 기술적 개선
- **Kotlin Coroutines**: 비동기 처리 최적화
- **에러 핸들링**: 세분화된 오류 처리 및 사용자 친화적 메시지
- **API 응답 구조**: 일관된 ApiResponse 패턴
- **JWT 보안**: 토큰 검증 및 자동 갱신

## 개발 가이드라인
- **클린 아키텍처**: 도메인 중심 설계 유지
- **리액티브 프로그래밍**: Mono/Flux 활용
- **테스트 우선**: 단위/통합 테스트 작성
- **코드 품질**: Kotlin 코딩 컨벤션 준수

## 프로젝트 현황 요약

**SimpleChatServer**는 Spring Boot WebFlux와 Kotlin Coroutines 기반의 현대적인 실시간 채팅 애플리케이션입니다. MVP 기능이 대부분 완료되었으며, 현재는 고급 기능 추가 및 사용자 경험 개선 단계에 있습니다.

### 핵심 성과
- **100% 리액티브**: WebFlux + R2DBC + MongoDB Reactive
- **현대적 UI**: Glassmorphism + 접근성 + 반응형
- **실시간 통신**: WebSocket + 온라인 상태
- **보안**: JWT 인증 + 권한 관리
- **확장성**: 멀티모듈 + 클린 아키텍처

### Task Master 설정
- **언어**: Korean (전체 문서 한국어)
- **현재 태그**: master
- **브랜치**: features/task_coroutines

이 문서는 SimpleChatServer의 현재 구현 상태와 향후 개발 방향을 제시하는 가이드입니다.
