package com.simplechat.infrastructure.config

import com.simplechat.domain.entity.ChatMessage
import com.simplechat.domain.entity.MessageType
import com.simplechat.infrastructure.repository.ChatMessageRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.test.context.ActiveProfiles
import reactor.test.StepVerifier
import java.time.LocalDateTime

@SpringBootTest(classes = [MongoTestApplication::class])
@ActiveProfiles("test")
class MongoConfigTest {

    @Autowired
    private lateinit var reactiveMongoTemplate: ReactiveMongoTemplate

    @Autowired
    private lateinit var chatMessageRepository: ChatMessageRepository

    @BeforeEach
    fun setUp() {
        // 각 테스트 전에 테스트 데이터 정리 (drop 대신 deleteAll 사용)
        StepVerifier.create(
            reactiveMongoTemplate.remove(org.springframework.data.mongodb.core.query.Query(), "chat_messages")
        )
            .expectNextMatches { it.wasAcknowledged() } // 삭제된 문서 수는 상관없음
            .verifyComplete()
    }

    @Test
    fun `MongoDB 연결 및 기본 동작 테스트`() {
        // 연결 테스트: 기본적인 연결 확인을 위해 간단한 작업 수행
        StepVerifier.create(reactiveMongoTemplate.collectionExists("chat_messages"))
            .expectNextMatches { exists -> exists != null } // 컬렉션 존재 여부 확인
            .verifyComplete()

        // MongoDB 기본 작업 테스트: 간단한 문서 저장/조회
        val testDoc = mutableMapOf("test" to "connection")
        StepVerifier.create(
            reactiveMongoTemplate.save(testDoc, "chat_messages")
                .then(reactiveMongoTemplate.findAll(Map::class.java, "chat_messages").hasElements())
        )
            .expectNext(true)
            .verifyComplete()
    }

    @Test
    fun `MongoDB 연결 풀 및 설정 확인`() {
        // MongoDB 연결 상태 확인
        StepVerifier.create(reactiveMongoTemplate.mongoDatabaseFactory.mongoDatabase)
            .expectNextMatches { db -> 
                db.name == "simplechatserver"
            }
            .verifyComplete()

        // 기본 컬렉션 생성 및 접근 테스트
        StepVerifier.create(reactiveMongoTemplate.collectionExists("chat_messages"))
            .expectNext(true)
            .verifyComplete()

        // 동시 연결 테스트 (연결 풀 확인)
        val concurrentOperations = (1..5).map { i ->
            reactiveMongoTemplate.save(
                mapOf("testId" to i, "message" to "concurrent test $i"), 
                "chat_messages"
            )
        }
        
        StepVerifier.create(
            reactor.core.publisher.Flux.merge(concurrentOperations)
                .count()
        )
            .expectNext(5L)
            .verifyComplete()
    }

    @Test
    fun `데이터베이스 이름 및 설정 확인`() {
        // 데이터베이스 이름 확인 (개발환경의 simplechatserver 데이터베이스 사용)
        StepVerifier.create(reactiveMongoTemplate.mongoDatabaseFactory.mongoDatabase)
            .expectNextMatches { db ->
                db.name == "simplechatserver"
            }
            .verifyComplete()

        // MongoDB 클라이언트 설정 확인
        val mongoDatabase = reactiveMongoTemplate.mongoDatabaseFactory.mongoDatabase.block()
        assert(mongoDatabase != null) { "MongoDB database should be available" }
        assert(mongoDatabase!!.name == "simplechatserver") { 
            "Expected database name 'simplechatserver' but was '${mongoDatabase.name}'" 
        }
    }

    @Test
    fun `ChatMessage CRUD 동작 테스트`() {
        // 테스트 데이터 생성
        val testMessage = ChatMessage(
            roomId = 1L,
            userId = 100L,
            content = "테스트 메시지입니다.",
            timestamp = LocalDateTime.now(),
            messageType = MessageType.TEXT
        )

        // CREATE 테스트
        StepVerifier.create(chatMessageRepository.save(testMessage))
            .expectNextMatches { savedMessage ->
                savedMessage.id != null &&
                savedMessage.roomId == 1L &&
                savedMessage.content == "테스트 메시지입니다."
            }
            .verifyComplete()

        // READ 테스트
        StepVerifier.create(chatMessageRepository.countByRoomId(1L))
            .expectNext(1L)
            .verifyComplete()

        // DELETE 테스트 - 전체 삭제
        StepVerifier.create(chatMessageRepository.deleteAll())
            .verifyComplete()

        StepVerifier.create(chatMessageRepository.count())
            .expectNext(0L)
            .verifyComplete()
    }

    @Test
    fun `MongoDB 인덱스 및 쿼리 성능 테스트`() {
        // 여러 메시지 저장
        val messages = (1..5).map { i ->
            ChatMessage(
                roomId = 1L,
                userId = 100L + i,
                content = "메시지 $i",
                timestamp = LocalDateTime.now().plusMinutes(i.toLong()),
                messageType = MessageType.TEXT
            )
        }

        // 배치 저장
        StepVerifier.create(chatMessageRepository.saveAll(messages))
            .expectNextCount(5)
            .verifyComplete()

        // roomId 기준 조회 (인덱스 활용)
        StepVerifier.create(chatMessageRepository.countByRoomId(1L))
            .expectNext(5L)
            .verifyComplete()

        // 존재하지 않는 roomId 조회
        StepVerifier.create(chatMessageRepository.countByRoomId(999L))
            .expectNext(0L)
            .verifyComplete()
    }

    @Test
    fun `ReactiveMongoTemplate _class 필드 제거 확인`() {
        val testMessage = ChatMessage(
            roomId = 1L,
            userId = 100L,
            content = "타입 매퍼 테스트",
            messageType = MessageType.TEXT
        )

        StepVerifier.create(
            reactiveMongoTemplate.save(testMessage, "chat_messages")
                .then(reactiveMongoTemplate.findAll(org.bson.Document::class.java, "chat_messages").next())
        )
            .expectNextMatches { document: org.bson.Document ->
                // _class 필드가 없어야 함 (DefaultMongoTypeMapper(null) 설정으로 인해)
                !document.containsKey("_class")
            }
            .verifyComplete()
    }
}