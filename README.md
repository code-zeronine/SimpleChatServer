# SimpleChatServer

실시간 채팅 애플리케이션으로, 현대적인 리액티브 아키텍처와 Kotlin Coroutines를 활용하여 높은 성능과 확장성을 제공합니다.

## 🚀 주요 특징

- **실시간 채팅**: WebSocket 기반 실시간 메시징
- **사용자 인증**: JWT 기반 보안 인증 시스템
- **채팅방 관리**: 공개/비공개 채팅방, 역할 기반 권한 관리
- **메시지 검색**: MongoDB 텍스트 검색 및 필터링
- **캐싱 시스템**: Redis를 활용한 메시지 캐싱 및 성능 최적화
- **반응형 아키텍처**: Kotlin Coroutines와 Spring WebFlux 기반

## 📋 최근 업데이트

**v0.0.1-SNAPSHOT (2024-12-09)**
- **🎯 Kotlin Coroutines 완전 전환**: Spring WebFlux Reactive Programming에서 Kotlin Coroutines로 완전 마이그레이션
- **⚡ 성능 개선**: 비동기 처리 최적화 및 코드 가독성 향상
- **🔧 채팅방 UX 개선**: 입장/퇴장 알림, 타이핑 상태 표시, 실시간 멤버 수 업데이트
- **📱 UI/UX 향상**: FontAwesome 아이콘, 로딩 스피너, 메시지 시간 표시 개선

## 🏗️ 아키텍처: Clean Architecture 3-Tier 모듈 구조

프로젝트는 역할에 따라 `domain`, `infrastructure`, `api` 세 개의 모듈로 명확하게 분리되어 있습니다.

### 📦 모듈 구조

#### **`domain` (핵심 비즈니스 로직)**
- **역할**: 순수한 비즈니스 로직과 도메인 규칙을 담당
- **특징**: 외부 프레임워크에 의존하지 않는 순수 Kotlin 코드
- **주요 컴포넌트**:
  - `ChatMessage`, `User`, `ChatRoom` - 핵심 엔티티
  - `MessageType`, `ChatRoomRole` - 값 객체 및 열거형
  - Repository 인터페이스 정의

#### **`infrastructure` (기술적 구현)**
- **역할**: 외부 시스템과의 연동 및 기술적 세부사항 처리
- **특징**: Spring WebFlux, MongoDB, Redis, WebSocket 구현
- **주요 컴포넌트**:
  - `ChatMessageRepositoryImpl` - MongoDB 기반 메시지 저장소
  - `WebSocketMessageHandler` - WebSocket 메시지 처리
  - `RedisMessageBrokerService` - Redis Pub/Sub 메시지 브로커
  - `MessageCacheService` - Redis 기반 메시지 캐싱

#### **`api` (애플리케이션 서비스)**
- **역할**: 외부 API 노출 및 비즈니스 플로우 조정
- **특징**: REST API, 트랜잭션 관리, 보안 처리
- **주요 컴포넌트**:
  - `MessageController`, `ChatRoomController` - REST API 엔드포인트
  - `AuthService`, `ChatRoomService` - 비즈니스 서비스 (Kotlin Coroutines)
  - 정적 웹 리소스 (HTML, CSS, JavaScript)

## 🛠️ 기술 스택

### **백엔드**
- **언어**: Kotlin 1.9.25
- **프레임워크**: Spring Boot 3.5.4 (WebFlux)
- **비동기 처리**: Kotlin Coroutines 1.8.1
- **보안**: Spring Security + JWT (JJWT 0.12.6)
- **데이터베이스**: 
  - PostgreSQL (R2DBC) - 사용자 및 채팅방 정보
  - MongoDB (Reactive) - 메시지 저장 및 검색
- **캐시/메시지 브로커**: Redis
- **실시간 통신**: WebSocket (네이티브 구현)
- **API 문서**: SpringDoc OpenAPI 3

### **프론트엔드**
- **구조**: 순수 JavaScript SPA (No Framework)
- **스타일링**: CSS3 + FontAwesome
- **실시간 통신**: WebSocket API
- **기능**: 반응형 UI, 실시간 메시징, 타이핑 표시기

### **인프라**
- **빌드 도구**: Gradle 8.14.3 (Multi-module)
- **Java**: OpenJDK 21
- **컨테이너**: Docker Compose
- **개발환경**: MongoDB, Redis, PostgreSQL

## 🚦 주요 기능

### **인증 및 보안**
- JWT 기반 사용자 인증
- 회원가입/로그인/토큰 갱신
- 역할 기반 접근 제어 (OWNER, ADMIN, MEMBER)

### **채팅방 관리**
- 공개/비공개 채팅방 생성
- 채팅방 참여/퇴장
- 실시간 멤버 수 업데이트
- 권한 기반 채팅방 관리

### **실시간 메시징**
- WebSocket 기반 실시간 채팅
- 타이핑 상태 표시
- 메시지 히스토리 조회 (페이지네이션)
- 텍스트 검색 및 필터링

### **성능 최적화**
- Redis 기반 메시지 캐싱
- 비동기 처리 (Kotlin Coroutines)
- 커넥션 풀링 및 리소스 최적화

## 🔧 개발 환경 설정

### **필요 사항**
- Java 21+
- Docker & Docker Compose
- Gradle 8.14+

### **실행 방법**

1. **저장소 클론**
```bash
git clone <repository-url>
cd SimpleChatServer
```

2. **의존 서비스 실행**
```bash
docker-compose up -d
```

3. **애플리케이션 빌드 및 실행**
```bash
./gradlew build
./gradlew bootRun
```

4. **접속**
- 웹 애플리케이션: http://localhost:8080
- API 문서: http://localhost:8080/swagger-ui.html
- 헬스체크: http://localhost:8080/actuator/health

## 📊 프로젝트 현황

### **개발 환경**
- **Java**: 21
- **Kotlin**: 1.9.25  
- **Gradle**: 8.14.3
- **Spring Boot**: 3.5.4
- **현재 브랜치**: `features/task_coroutines`

### **프로젝트 관리**
- **태스크 관리**: Task Master AI (.taskmaster/)
- **문서화**: PRD 기반 개발 로드맵
- **버전 관리**: Git (feature-branch 전략)
