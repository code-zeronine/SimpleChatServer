# SimpleChatServer
이 프로젝트는 기본적인 채팅 서버를 위한 프로젝트입니다.

## 프로젝트 구조

이 프로젝트는 최신 기술 스택으로 구축된 실시간 채팅 애플리케이션으로, 확장성과 유지보수성을 고려한 모듈형 아키텍처를 채택하고 있습니다.

### 아키텍처: 3-Tier 모듈 구조

프로젝트는 역할에 따라 `domain`, `infrastructure`, `api` 세 개의 모듈로 명확하게 분리되어 있습니다. 이는 계층 간의 의존성을 낮추고 각자의 역할에 집중하게 하는 현대적인 소프트웨어 설계 방식입니다.

-   **`domain` (핵심 비즈니스 로직)**
    -   **역할:** 프로젝트의 가장 핵심적인 부분으로, 순수한 비즈니스 로직과 데이터 모델(Entity)을 담고 있습니다.
    -   **특징:** 특정 기술이나 프레임워크에 의존하지 않으며, 채팅 메시지, 사용자, 채팅방 등 도메인 자체의 규칙과 상태를 정의합니다.
    -   **주요 파일:** `ChatMessage.kt`, `WebSocketMessage.kt`

-   **`infrastructure` (외부 기술 연동)**
    -   **역할:** `domain` 계층의 인터페이스를 실제로 구현하며, 외부 기술과의 연동을 책임집니다.
    -   **특징:** 데이터베이스(MongoDB), 캐시/메시지 브로커(Redis), 웹소켓 처리 등 외부 시스템과의 통신 및 데이터 영속성을 관리합니다.
    -   **주요 파일:** `ChatMessageRepository.kt`, `WebSocketMessageHandler.kt`, `RedisMessageBrokerService.kt`

-   **`api` (외부 노출 및 UI)**
    -   **역할:** 외부 사용자와 시스템의 상호작용을 위한 진입점 역할을 합니다.
    -   **특징:** RESTful API 컨트롤러를 통해 외부 요청을 받고, `infrastructure` 계층을 호출하여 비즈니스 로직을 실행합니다. 또한, 사용자가 직접 상호작용하는 프론트엔드(HTML, CSS, JavaScript) 정적 파일을 포함하고 있습니다.
    -   **주요 파일:** `MessageController.kt`, `AuthController.kt`, `chat.html`, `chat.js`

### 기술 스택

-   **백엔드:**
    -   **언어/프레임워크:** Kotlin, Spring Boot (WebFlux)
    -   **실시간 통신:** WebSocket (STOMP를 사용하지 않는 순수 웹소켓 구현)
    -   **데이터베이스:** MongoDB (메시지 저장 및 텍스트 검색)
    -   **캐시 & 메시지 브로커:** Redis (최근 메시지 캐싱 및 웹소켓 메시지 Pub/Sub)

-   **프론트엔드:**
    -   **구조:** 별도의 프레임워크(React, Vue 등) 없이 순수 JavaScript, HTML, CSS로 구현된 단일 페이지 애플리케이션(SPA)입니다.
    -   **특징:** `websocket-client.js`에서 웹소켓 연결을 관리하고, 각 페이지(`login`, `rooms`, `chat`)별로 JavaScript 파일을 분리하여 기능을 구현합니다.

### 개발 및 운영 환경

-   **빌드 시스템:** Gradle (멀티 모듈 프로젝트 구성)
-   **개발 환경:** Docker (`docker-compose.yml`)를 사용하여 MongoDB, Redis 등 개발에 필요한 서비스를 컨테이너로 관리합니다.
-   **프로젝트 관리:** `.taskmaster` 디렉터리를 통해 요구사항 문서(PRD)와 태스크를 체계적으로 관리하고 있습니다.

### 개발 환경 상세

- **Java:** 21
- **Kotlin:** 1.9.25
- **Gradle:** 8.14.3
- **Spring Boot:** 3.5.4
