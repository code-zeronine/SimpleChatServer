# 중복 로그인 제어 기능 테스트 가이드

## 개요
SimpleChatServer의 중복 로그인 제어 기능에 대한 테스트 가이드입니다.

## 구현된 기능

### 1. 중복 로그인 감지 및 제어
- **SessionInvalidationService**: 세션 무효화 관리
- **WebSocketSessionManager**: 중복 로그인 감지 및 알림
- **AuthService**: 로그인 시 중복 감지 로직 통합

### 2. WebSocket 알림 메시지
- **DuplicateLoginWebSocketMessage**: 중복 로그인 알림
- **SessionTerminatedWebSocketMessage**: 세션 종료 알림
- **WebSocketMessageHandler**: 메시지 전송 처리

### 3. REST API
- **SessionManagementController**: 사용자 세션 관리 API
  - `GET /api/sessions/active`: 활성 세션 조회
  - `DELETE /api/sessions/{sessionId}`: 특정 세션 종료
  - `DELETE /api/sessions/others`: 다른 세션 모두 종료
  - `GET /api/sessions/stats`: 세션 통계 조회

### 4. 설정 관리
- **DuplicateLoginConfig**: 중복 로그인 제어 설정
- 설정 파일: `duplicate-login-config.yml`

## 테스트 시나리오

### 시나리오 1: 기본 중복 로그인 감지
1. 사용자 A가 브라우저 1에서 로그인
2. 사용자 A가 브라우저 2에서 로그인 시도
3. 브라우저 1에서 중복 로그인 알림 수신 확인
4. 두 세션 모두 활성 상태 유지 확인

### 시나리오 2: 세션 강제 종료
1. 사용자 A가 여러 기기에서 로그인
2. REST API를 통해 활성 세션 목록 조회
3. 특정 세션 강제 종료 요청
4. 해당 세션에서 연결 종료 확인

### 시나리오 3: 모든 다른 세션 종료
1. 사용자 A가 3개 기기에서 로그인
2. 한 기기에서 "다른 기기 모두 로그아웃" 실행
3. 현재 기기 제외한 2개 세션 종료 확인

## API 테스트 예시

### 1. 활성 세션 조회
```bash
curl -X GET "http://localhost:8080/api/sessions/active" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### 2. 특정 세션 종료
```bash
curl -X DELETE "http://localhost:8080/api/sessions/session-id-here" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### 3. 다른 세션 모두 종료
```bash
curl -X DELETE "http://localhost:8080/api/sessions/others" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### 4. 세션 통계 조회
```bash
curl -X GET "http://localhost:8080/api/sessions/stats" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

## WebSocket 메시지 형식

### 중복 로그인 알림
```json
{
  "type": "DUPLICATE_LOGIN_DETECTED",
  "timestamp": "2024-01-01T10:00:00Z",
  "message": "다른 위치에서 로그인이 감지되었습니다.",
  "newLoginInfo": {
    "loginTime": "2024-01-01T10:00:00Z",
    "ipAddress": "192.168.1.100",
    "userAgent": "Mozilla/5.0..."
  },
  "action": "NOTIFY_ONLY"
}
```

### 세션 종료 알림
```json
{
  "type": "SESSION_TERMINATED",
  "timestamp": "2024-01-01T10:00:00Z",
  "reason": "DUPLICATE_LOGIN",
  "message": "중복 로그인으로 인해 세션이 종료됩니다.",
  "gracePeriodSeconds": 5
}
```

## 설정 옵션

### application.yml 설정 예시
```yaml
simplechat:
  security:
    duplicate-login:
      enabled: true
      action: NOTIFY_ONLY  # NOTIFY_ONLY, FORCE_LOGOUT_OTHERS, DENY_NEW_LOGIN, ASK_USER_CHOICE
      max-concurrent-sessions: 0  # 0 = 무제한
      notification-timeout-seconds: 300
      logout-grace-period-seconds: 10
      allow-same-ip-login: true
      exclude-admin-users: true
      session-timeout-seconds: 1800
      login-history-retention-days: 30
```

## 성능 고려사항

### 메모리 사용량
- 세션당 약 1KB의 메타데이터
- 1000명 동시 접속 시 약 1MB 메모리 사용

### 확장성
- Redis 클러스터 환경에서 세션 정보 공유 가능
- 로드 밸런서 환경에서도 정상 동작

## 보안 고려사항

### 세션 보안
- JWT 토큰 기반 인증
- 세션 타임아웃 자동 처리
- IP 기반 접근 제어 옵션

### 개인정보 보호
- 로그인 이력 자동 삭제
- 민감한 정보 암호화 저장

## 문제 해결

### 일반적인 문제들

1. **세션이 정리되지 않는 경우**
   - WebSocket 연결 상태 확인
   - 스케줄러 동작 상태 확인

2. **알림이 전송되지 않는 경우**
   - Redis 연결 상태 확인
   - WebSocketMessageHandler 로그 확인

3. **성능 저하 발생 시**
   - 세션 정리 주기 조정
   - 불필요한 세션 수동 정리

### 로그 확인
```bash
# 중복 로그인 관련 로그
grep "Duplicate login" logs/application.log

# 세션 관리 관련 로그  
grep "Session" logs/application.log

# WebSocket 관련 로그
grep "WebSocket" logs/application.log
```

## 개발자 참고사항

### 주요 클래스들
- `SessionInvalidationService`: 세션 무효화 비즈니스 로직
- `WebSocketSessionManager`: 세션 생명주기 관리
- `WebSocketMessageHandler`: WebSocket 메시지 처리
- `SessionManagementController`: REST API 엔드포인트
- `DuplicateLoginConfig`: 설정 관리

### 확장 가능한 기능들
- 로그인 이력 저장 및 분석
- 이상 로그인 패턴 감지
- 관리자 대시보드 연동
- 모바일 푸시 알림 연동