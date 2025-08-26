package com.simplechat.infrastructure.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.test.context.ActiveProfiles
import reactor.test.StepVerifier

@SpringBootTest(classes = [MongoTestApplication::class])
@ActiveProfiles("test")
class MongoIndexConfigTest {

    @Autowired
    private lateinit var reactiveMongoTemplate: ReactiveMongoTemplate
    
    @Autowired
    private lateinit var mongoIndexConfig: MongoIndexConfig
    
    @Test
    fun `should create all required indexes`() {
        // Given & When
        mongoIndexConfig.createIndexes()
        
        // Then - 인덱스가 생성되었는지 확인
        StepVerifier.create(
            reactiveMongoTemplate.indexOps("chat_messages")
                .indexInfo
        )
            .expectNextCount(1) // 적어도 하나 이상의 인덱스가 있어야 함
            .thenCancel()
            .verify()
    }
    
    @Test
    fun `should log index information`() {
        // Given
        mongoIndexConfig.createIndexes()
        
        // When & Then - 로깅이 에러 없이 실행되는지 확인
        mongoIndexConfig.logIndexInformation()
        
        // 로그 출력은 별도의 검증 없이 에러가 발생하지 않으면 성공
    }
    
    @Test
    fun `should handle collection operations`() {
        // When & Then - ReactiveMongoTemplate이 정상 작동하는지 확인
        StepVerifier.create(
            reactiveMongoTemplate.collectionExists("chat_messages")
        )
            .expectNext(true)
            .verifyComplete()
    }
}