# Task Master Commands and Task Overview (Korean)

아래 안내와 작업 목록은 이 저장소의 Task Master 설정(.taskmaster/config.json, state.json, docs/prd.txt, reports/*)을 기반으로 합니다. CLAUDE.md에서 이 파일을 import하여, 대화형 에이전트에서 바로 확인/활용할 수 있습니다.

## 어떻게 사용하나요?
- 전역 설치(선택): `npm install -g task-master-ai`
- 빠른 확인: `task-master --version`
- PRD 파싱 예시: `task-master parse-prd ./.taskmaster/docs/prd.txt -n 10 --lang ko`
- 모델 상태 확인: `task-master models --status`

자세한 설치 가이드는 `.claude/commands/tm/setup/*` 문서를 참고하세요.

## 현재 프로젝트 작업 목록 (Task Master 리포트 요약)
다음 항목은 `.taskmaster/reports/task-complexity-report.json`에서 집계된 상위 작업들입니다.

1. Spring Boot WebFlux 프로젝트 초기 설정 및 Docker 환경 구성 (Gradle 8.14, Kotlin 2.0.21)
   - 복잡도: 7 / 권장 하위 작업 수: 7
2. PostgreSQL R2DBC 연동 및 사용자 도메인 구현
   - 복잡도: 6 / 권장 하위 작업 수: 6
3. JWT 기반 사용자 인증 시스템 구현
   - 복잡도: 8 / 권장 하위 작업 수: 8
4. 채팅방 관리 API 구현
   - 복잡도: 5 / 권장 하위 작업 수: 5
5. MongoDB 연동 및 메시지 저장소 구현
   - 복잡도: 6 / 권장 하위 작업 수: 6
6. WebSocket STOMP 실시간 메시징 엔진 구현
   - 복잡도: 9 / 권장 하위 작업 수: 9
7. 메시지 히스토리 조회 API 및 페이지네이션 구현
   - 복잡도: 5 / 권장 하위 작업 수: 5
8. Redis 세션 관리 및 메시지 브로커 구현
   - 복잡도: 6 / 권장 하위 작업 수: 6
9. 핵심 비즈니스 로직 단위 테스트 구현
   - 복잡도: 7 / 권장 하위 작업 수: 7
10. 통합 테스트 환경 구성 및 성능 최적화
   - 복잡도: 8 / 권장 하위 작업 수: 8

## PRD 기반 로드맵 개요 (요약)
`.taskmaster/docs/prd.txt` 기준:
- MVP(1단계):
  - Foundation Setup, User Authentication, Chat Room Management,
  - Real-time Messaging Core, Message Persistence, Quality Assurance
- 이후 단계: 확장성 개선, 고급 기능(1:1 DM, 파일 업로드 등), UX 향상, 관리자/분석 기능

## 편의 명령어(참고)
- /project:task-master:quick-install — 빠른 설치 안내
- /project:task-master:install — 상세 설치/트러블슈팅 안내
- /project:task-master:parse-prd — PRD를 파싱해 작업 생성

위 명령어들은 이 문서의 안내를 기반으로 한 작업 흐름을 의미합니다(실행은 로컬 환경에서 task-master CLI로 수행).

## 메모
- 설정 언어: Korean (config.json: global.responseLanguage)
- 현재 태그: master (state.json: currentTag)
- 마지막 스위치: state.json 참조

이 문서는 Task Master를 이용해 "다음 작업 내용"을 한눈에 표시하기 위한 요약 뷰로 사용됩니다.
