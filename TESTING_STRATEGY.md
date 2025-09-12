# Infrastructure 모듈 테스트 전략

## 📋 개요

SimpleChatServer Infrastructure 모듈은 Spring Boot WebFlux, Kotlin Coroutines, R2DBC PostgreSQL, MongoDB Reactive, Redis Reactive를 사용하는 리액티브 아키텍처입니다. 본 문서는 이러한 기술 스택에 최적화된 테스트 전략을 제시합니다.

## 🏗️ 현재 Infrastructure 모듈 구성

### 핵심 기술 스택
- **R2DBC (PostgreSQL)**: 리액티브 관계형 데이터베이스 접근
- **MongoDB Reactive**: 리액티브 문서형 데이터베이스
- **Redis Reactive**: 리액티브 캐싱 및 메시징
- **Kotlin Coroutines**: 비동기 처리
- **Spring WebFlux**: 리액티브 웹 프레임워크

### 주요 컴포넌트
- **Repository 구현체**: R2DBC, MongoDB 데이터 접근 계층
- **메시징 서비스**: Redis PubSub, WebSocket 실시간 통신
- **보안 서비스**: JWT 토큰 관리, WebSocket 인증
- **모니터링 서비스**: 커스텀 메트릭, 에러 추적
- **세션 관리**: WebSocket 세션, 사용자 상태 관리

## 🎯 테스트 전략

### 1. 테스트 레이어 구분

#### A. 단위 테스트 (Unit Tests)
**목적**: 개별 컴포넌트의 비즈니스 로직 검증  
**범위**: 서비스 클래스, 유틸리티 클래스, 순수 함수  
**도구**: MockK, Kotest, `runTest`  

```kotlin
@Test
fun `메시지 라우팅 로직 테스트`() = runTest {
    // Given
    val sessionManager = mockk<WebSocketSessionManager>()
    val redisService = mockk<RedisMessageBrokerService>()
    
    every { sessionManager.getSessionsByChatRoom(any()) } returns emptyList()
    every { redisService.publishMessage(any(), any()) } returns Mono.just(1L)
    
    // When
    val result = messageRoutingService.routeMessage(chatMessage, sessionId)
    
    // Then
    StepVerifier.create(result)
        .verifyComplete()
    
    verify { redisService.publishMessage("chat:room:101", chatMessage) }
}
```

#### B. 통합 테스트 (Integration Tests)
**목적**: 외부 시스템과의 연동 검증  
**범위**: Repository 구현체, 데이터베이스 연동, 메시징 시스템  
**도구**: TestContainers, Spring Boot Test Slices  

```kotlin
@DataR2dbcTest
@Import(TestContainersConfig::class, UserRepositoryImpl::class)
class UserRepositoryIntegrationTest : BehaviorSpec() {
    
    @Autowired
    private lateinit var userRepository: UserRepositoryImpl
    
    @Test
    fun `사용자 저장 및 조회 검증`() = runTest {
        // Given
        val user = User(
            email = "test@example.com",
            passwordHash = "hashedPassword123",
            nickname = "testuser"
        )
        
        // When
        val savedUser = userRepository.save(user).awaitSingle()
        val foundUser = userRepository.findByEmail("test@example.com").awaitSingle()
        
        // Then
        foundUser.email shouldBe user.email
        foundUser.id shouldNotBe null
    }
}
```

#### C. End-to-End 테스트
**목적**: 전체 시스템 플로우 검증  
**범위**: API 엔드포인트부터 데이터베이스까지의 전체 흐름  
**도구**: WebTestClient, TestContainers  

## 🔧 기술별 테스트 방법

### R2DBC (PostgreSQL) 테스트

#### 설정
```kotlin
@TestConfiguration
class TestContainersConfig {
    companion object {
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("test-schema.sql")
            .withReuse(true)
            .apply { start() }
    }
    
    @Bean
    @Primary
    fun testConnectionFactory(): ConnectionFactory {
        return PostgresqlConnectionFactory(
            PostgresqlConnectionConfiguration.builder()
                .host(postgres.host)
                .port(postgres.getMappedPort(5432))
                .database(postgres.databaseName)
                .username(postgres.username)
                .password(postgres.password)
                .build()
        )
    }
}
```

#### 테스트 예시
```kotlin
@DataR2dbcTest
@Import(TestContainersConfig::class)
class ChatRoomRepositoryTest : BehaviorSpec() {
    
    @Autowired
    private lateinit var repository: ChatRoomRepositoryImpl
    
    @Test
    fun `채팅방 생성 및 참여자 관리 테스트`() = runTest {
        // Given
        val chatRoom = ChatRoom(
            name = "테스트 채팅방",
            description = "테스트용",
            isPublic = true
        )
        
        // When
        val saved = repository.save(chatRoom).awaitSingle()
        repository.addParticipant(saved.id!!, 1L).awaitSingleOrNull()
        
        // Then
        val participants = repository.findParticipants(saved.id!!).collectList().awaitSingle()
        participants.size shouldBe 1
    }
}
```

### MongoDB Reactive 테스트

#### 설정
```kotlin
@TestConfiguration
class MongoTestConfig {
    companion object {
        val mongodb: MongoDBContainer = MongoDBContainer("mongo:7.0")
            .withReuse(true)
            .apply { start() }
    }
    
    @Bean
    @Primary
    fun testReactiveMongoTemplate(): ReactiveMongoTemplate {
        val connectionString = ConnectionString(
            "mongodb://${mongodb.host}:${mongodb.getMappedPort(27017)}/testdb"
        )
        val factory = SimpleReactiveMongoDatabaseFactory(connectionString)
        return ReactiveMongoTemplate(factory)
    }
}
```

#### 테스트 예시
```kotlin
@DataMongoTest
@Import(MongoTestConfig::class)
class ChatMessageRepositoryTest : BehaviorSpec() {
    
    @Autowired
    private lateinit var repository: ChatMessageRepositoryImpl
    
    @Test
    fun `메시지 저장 및 페이지네이션 테스트`() = runTest {
        // Given - 100개의 테스트 메시지 생성
        val messages = (1..100).map { index ->
            ChatMessage(
                roomId = "room1",
                userId = 1L,
                content = "Test message $index",
                messageType = MessageType.TEXT
            )
        }
        
        // When
        repository.saveAll(messages).collectList().awaitSingle()
        
        val page = repository.findByRoomIdWithPagination(
            "room1", 
            PageRequest.of(0, 10)
        ).collectList().awaitSingle()
        
        // Then
        page.size shouldBe 10
        page.first().content shouldContain "Test message"
    }
    
    @Test
    fun `메시지 검색 기능 테스트`() = runTest {
        // Given
        val searchableMessage = ChatMessage(
            roomId = "room1",
            userId = 1L,
            content = "중요한 알림: 시스템 점검 예정",
            messageType = MessageType.TEXT
        )
        repository.save(searchableMessage).awaitSingle()
        
        // When
        val searchResults = repository.searchMessages("room1", "시스템 점검")
            .collectList().awaitSingle()
        
        // Then
        searchResults.size shouldBe 1
        searchResults.first().content shouldContain "시스템 점검"
    }
}
```

### Redis Reactive 테스트

#### 설정
```kotlin
@TestConfiguration
class RedisTestConfig {
    companion object {
        val redis: GenericContainer<*> = GenericContainer("redis:7-alpine")
            .withExposedPorts(6379)
            .withReuse(true)
            .apply { start() }
    }
    
    @Bean
    @Primary
    fun testReactiveRedisTemplate(): ReactiveRedisTemplate<String, String> {
        val factory = LettuceConnectionFactory(redis.host, redis.getMappedPort(6379))
        factory.afterPropertiesSet()
        return ReactiveRedisTemplate(factory, RedisSerializationContext.string())
    }
}
```

#### 테스트 예시
```kotlin
@DataRedisTest
@Import(RedisTestConfig::class)
class RedisMessageBrokerServiceTest : BehaviorSpec() {
    
    @Autowired
    private lateinit var redisService: RedisMessageBrokerService
    
    @Test
    fun `Redis PubSub 메시지 발행 및 구독 테스트`() = runTest {
        // Given
        val channel = "test:channel"
        val message = SystemWebSocketMessage(
            messageId = "test-msg",
            timestamp = System.currentTimeMillis(),
            sessionId = null,
            content = "테스트 시스템 메시지"
        )
        val received = mutableListOf<WebSocketMessage>()
        
        // When
        val subscription = redisService.subscribeToChannel(channel)
            .doOnNext { received.add(it) }
            .subscribe()
        
        delay(100) // 구독 설정 대기
        redisService.publishMessage(channel, message).awaitSingle()
        delay(100) // 메시지 처리 대기
        
        // Then
        received.size shouldBe 1
        received.first().content shouldBe message.content
        
        subscription.dispose()
    }
    
    @Test
    fun `Redis 캐싱 기능 테스트`() = runTest {
        // Given
        val cacheKey = "user:session:123"
        val sessionData = SessionMetadata(
            userId = 123L,
            sessionId = "session-123",
            joinedAt = Instant.now()
        )
        
        // When
        redisService.cacheSessionData(cacheKey, sessionData).awaitSingle()
        val cached = redisService.getSessionData(cacheKey).awaitSingle()
        
        // Then
        cached.userId shouldBe sessionData.userId
        cached.sessionId shouldBe sessionData.sessionId
    }
}
```

### Kotlin Coroutines 테스트

#### 기본 테스트 패턴
```kotlin
class CoroutineServiceTest : BehaviorSpec() {
    
    @Test
    fun `코루틴 동시성 처리 테스트`() = runTest {
        // Given
        val service = MessageProcessingService()
        val messages = (1..10).map { createTestMessage(it) }
        val startTime = currentTime
        
        // When - 동시 처리
        val results = messages.map { message ->
            async {
                service.processMessage(message)
            }
        }.awaitAll()
        
        // Then
        val processingTime = currentTime - startTime
        results.size shouldBe 10
        processingTime shouldBeLessThan 1000 // 병렬 처리로 시간 단축
    }
    
    @Test
    fun `코루틴 예외 처리 테스트`() = runTest {
        // Given
        val service = MessageProcessingService()
        
        // When & Then
        assertFailsWith<MessageProcessingException> {
            service.processInvalidMessage(invalidMessage)
        }
    }
    
    @Test
    fun `코루틴 취소 처리 테스트`() = runTest {
        // Given
        val service = LongRunningService()
        
        // When
        val job = launch {
            service.longRunningTask()
        }
        
        delay(100)
        job.cancel()
        
        // Then
        job.isCancelled shouldBe true
    }
}
```

#### Flow 테스트
```kotlin
@Test
fun `Flow 데이터 스트림 테스트`() = runTest {
    // Given
    val messageFlow = messageService.getMessageStream("room1")
    val received = mutableListOf<ChatMessage>()
    
    // When
    val job = launch {
        messageFlow.collect { message ->
            received.add(message)
        }
    }
    
    // 테스트 메시지 발송
    messageService.sendMessage(createTestMessage("room1", "Hello"))
    messageService.sendMessage(createTestMessage("room1", "World"))
    
    advanceTimeBy(1000)
    job.cancel()
    
    // Then
    received.size shouldBe 2
    received[0].content shouldBe "Hello"
    received[1].content shouldBe "World"
}
```

### Spring WebFlux 테스트

#### WebTestClient 사용
```kotlin
@WebFluxTest(ChatWebSocketHandler::class)
@Import(TestSecurityConfig::class)
class WebSocketHandlerTest {
    
    @Autowired
    private lateinit var webTestClient: WebTestClient
    
    @Test
    fun `WebSocket 연결 성공 테스트`() {
        webTestClient.get()
            .uri("/chat/websocket")
            .header("Authorization", "Bearer $validJwtToken")
            .exchange()
            .expectStatus().isSwitchingProtocols()
    }
    
    @Test
    fun `인증 실패 시 WebSocket 연결 거부 테스트`() {
        webTestClient.get()
            .uri("/chat/websocket")
            .header("Authorization", "Bearer $invalidToken")
            .exchange()
            .expectStatus().isUnauthorized()
    }
}
```

#### 리액티브 컨트롤러 테스트
```kotlin
@WebFluxTest(ChatRoomController::class)
class ChatRoomControllerTest {
    
    @Autowired
    private lateinit var webTestClient: WebTestClient
    
    @MockBean
    private lateinit var chatRoomService: ChatRoomService
    
    @Test
    fun `채팅방 목록 조회 테스트`() {
        // Given
        val mockRooms = listOf(
            ChatRoom(id = 1L, name = "Room 1"),
            ChatRoom(id = 2L, name = "Room 2")
        )
        every { chatRoomService.getAllRooms() } returns Flux.fromIterable(mockRooms)
        
        // When & Then
        webTestClient.get()
            .uri("/api/chat-rooms")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].name").isEqualTo("Room 1")
    }
}
```

## 🧪 테스트 유틸리티 및 헬퍼

### 테스트 데이터 팩토리
```kotlin
object TestDataFactory {
    
    fun createTestUser(
        email: String = "test@example.com",
        nickname: String = "testuser"
    ): User {
        return User(
            email = email,
            passwordHash = "hashed_password_123",
            nickname = nickname,
            createdAt = Instant.now()
        )
    }
    
    fun createTestChatRoom(
        name: String = "테스트 채팅방",
        isPublic: Boolean = true
    ): ChatRoom {
        return ChatRoom(
            name = name,
            description = "테스트용 채팅방",
            isPublic = isPublic,
            createdBy = 1L,
            createdAt = Instant.now()
        )
    }
    
    fun createTestMessage(
        roomId: String = "room1",
        content: String = "Test message",
        userId: Long = 1L
    ): ChatMessage {
        return ChatMessage(
            roomId = roomId,
            userId = userId,
            content = content,
            messageType = MessageType.TEXT,
            timestamp = Instant.now()
        )
    }
}
```

### 코루틴 테스트 확장 함수
```kotlin
// Mono/Flux를 코루틴에서 쉽게 테스트하기 위한 확장 함수
suspend fun <T> Mono<T>.awaitWithTimeout(
    timeout: Duration = 1.seconds
): T = this.timeout(timeout).awaitSingle()

suspend fun <T> Flux<T>.collectWithTimeout(
    timeout: Duration = 1.seconds
): List<T> = this.timeout(timeout).collectList().awaitSingle()

// 가상 시간을 사용한 지연 테스트
suspend fun TestScope.delayAndAdvance(delayMs: Long) {
    delay(delayMs)
    advanceTimeBy(delayMs)
}
```

### 커스텀 매처
```kotlin
// Kotest 커스텀 매처
fun beValidMessage() = object : Matcher<ChatMessage> {
    override fun test(value: ChatMessage): MatcherResult {
        val valid = value.content.isNotBlank() && 
                   value.userId > 0 && 
                   value.roomId.isNotBlank()
        
        return MatcherResult(
            valid,
            { "Message should be valid but was $value" },
            { "Message should not be valid but was $value" }
        )
    }
}

// 사용 예시
message should beValidMessage()
```

## 📊 권장 테스트 구조

```
infrastructure/src/test/kotlin/
├── config/
│   ├── TestContainersConfig.kt      # TestContainers 설정
│   ├── TestDataFactory.kt           # 테스트 데이터 생성
│   ├── TestSecurityConfig.kt        # 테스트용 보안 설정
│   └── TestUtils.kt                 # 테스트 유틸리티
├── repository/
│   ├── UserRepositoryIntegrationTest.kt
│   ├── ChatMessageRepositoryTest.kt
│   ├── ChatRoomRepositoryTest.kt
│   └── UserChatRoomRepositoryTest.kt
├── messaging/
│   ├── RedisMessageBrokerServiceTest.kt
│   ├── MessageRoutingServiceTest.kt
│   ├── RedisPubSubServiceTest.kt
│   └── MessageCacheServiceTest.kt
├── security/
│   ├── JwtTokenProviderTest.kt
│   ├── WebSocketAuthServiceTest.kt
│   └── JwtSessionServiceTest.kt
├── websocket/
│   ├── ChatWebSocketHandlerTest.kt
│   ├── WebSocketSessionManagerTest.kt
│   ├── WebSocketMessageHandlerTest.kt
│   └── WebSocketErrorHandlerTest.kt
├── monitoring/
│   ├── CustomMetricsServiceTest.kt
│   ├── RedisMonitoringServiceTest.kt
│   └── WebSocketErrorMetricsTest.kt
└── integration/
    ├── DatabaseIntegrationTest.kt    # DB 간 연동 테스트
    ├── MessageFlowIntegrationTest.kt # 전체 메시지 플로우
    └── FullStackIntegrationTest.kt   # 전체 시스템 통합
```

## 🚀 성능 최적화 및 모범 사례

### TestContainers 최적화
```kotlin
// 컨테이너 재사용으로 테스트 속도 향상
companion object {
    val sharedPostgres = PostgreSQLContainer("postgres:15-alpine")
        .withReuse(true)
        .withLabel("reuse-id", "postgres-shared")
        .apply { start() }
}

// 병렬 테스트 지원
@EnabledIf("java.util.concurrent.Runtime.getRuntime().availableProcessors() > 2")
@Execution(ExecutionMode.CONCURRENT)
class ParallelExecutionTest : BehaviorSpec()
```

### 메모리 및 시간 최적화
```kotlin
// 테스트 데이터 최소화
@BeforeEach
fun setUp() = runTest {
    // 필요한 최소한의 데이터만 생성
    testUser = userRepository.save(createMinimalTestUser()).awaitSingle()
}

// 가상 시간 활용
@Test
fun `시간 의존적 로직 테스트`() = runTest {
    val startTime = currentTime
    
    // delay() 호출이 즉시 완료됨
    service.processWithDelay()
    
    val elapsed = currentTime - startTime
    elapsed shouldBe 0 // 실제 시간 경과 없음
}
```

### 테스트 태깅 및 분류
```kotlin
// 느린 테스트 분류
@Tag("slow")
@Tag("integration")
class DatabaseIntegrationTest : BehaviorSpec()

// 빠른 테스트만 실행
// ./gradlew test -Dkotest.tags="!slow"

// 통합 테스트만 실행  
// ./gradlew test -Dkotest.tags="integration"
```

## 🔍 디버깅 및 트러블슈팅

### 일반적인 문제와 해결책

#### 1. TestContainers 포트 충돌
```kotlin
// 동적 포트 할당 사용
val postgres = PostgreSQLContainer("postgres:15-alpine")
    .withExposedPorts() // 기본 포트 사용하지 않음
    
val port = postgres.getMappedPort(5432) // 동적으로 할당된 포트 사용
```

#### 2. 코루틴 테스트 시간 초과
```kotlin
// 적절한 타임아웃 설정
@Test
fun longRunningTest() = runTest(timeout = 30.seconds) {
    // 오래 걸리는 작업
    service.heavyOperation()
}
```

#### 3. 리액티브 스트림 테스트 실패
```kotlin
// StepVerifier를 사용한 정확한 검증
@Test
fun reactiveStreamTest() = runTest {
    StepVerifier.create(service.getDataStream())
        .expectNext(expectedData1)
        .expectNext(expectedData2)
        .verifyComplete()
}
```

## 📈 테스트 커버리지 목표

- **단위 테스트**: 80% 이상
- **통합 테스트**: 핵심 플로우 100%
- **End-to-End 테스트**: 주요 사용자 시나리오 100%

## 🔄 지속적 통합 (CI) 설정

```yaml
# GitHub Actions 예시
name: Infrastructure Tests
on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      
      # TestContainers를 위한 Docker 설정
      - name: Start containers
        run: docker-compose -f docker-compose.test.yml up -d
      
      # 테스트 실행
      - name: Run tests
        run: ./gradlew infrastructure:test
      
      # 테스트 결과 업로드
      - name: Upload test results
        uses: actions/upload-artifact@v3
        with:
          name: test-results
          path: infrastructure/build/reports/tests/
```

이 전략을 통해 Infrastructure 모듈의 모든 컴포넌트를 체계적이고 효율적으로 테스트할 수 있으며, 리액티브 프로그래밍 패러다임에 최적화된 테스트 환경을 구축할 수 있습니다.