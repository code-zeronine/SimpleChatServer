package com.simplechat.infrastructure.service

import com.simplechat.infrastructure.config.MongoTestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.test.context.ActiveProfiles
import reactor.test.StepVerifier

@SpringBootTest(classes = [MongoTestApplication::class])
@ActiveProfiles("test")
class MongoIndexServiceTest {

    @Autowired
    private lateinit var mongoIndexService: MongoIndexService
    
    @Autowired
    private lateinit var reactiveMongoTemplate: ReactiveMongoTemplate
    
    @BeforeEach
    fun setUp() {
        // 테스트 전 컬렉션이 존재하는지 확인
        StepVerifier.create(
            reactiveMongoTemplate.collectionExists("chat_messages")
                .flatMap { exists ->
                    if (!exists) {
                        reactiveMongoTemplate.createCollection("chat_messages")
                    } else {
                        reactor.core.publisher.Mono.just(exists)
                    }
                }
        ).expectNextCount(1)
            .verifyComplete()
    }
    
    @Test
    fun `should get all indexes`() {
        // When & Then
        StepVerifier.create(mongoIndexService.getAllIndexes())
            .expectNextCount(1) // 최소 _id 인덱스는 존재해야 함
            .thenCancel()
            .verify()
    }
    
    @Test
    fun `should analyze index usage`() {
        // When & Then
        StepVerifier.create(mongoIndexService.analyzeIndexUsage())
            .expectNextMatches { result ->
                result is Map<*, *> && result.isNotEmpty()
            }
            .verifyComplete()
    }
    
    @Test
    fun `should analyze query performance`() {
        // Given
        val testRoomId = 1L
        val limit = 10
        
        // When & Then
        StepVerifier.create(mongoIndexService.analyzeQueryPerformance(testRoomId, limit))
            .expectNextMatches { result ->
                result["queryType"] == "roomId_with_timestamp_sort" &&
                result["roomId"] == testRoomId &&
                result["limit"] == limit &&
                result.containsKey("executionTimeMs") &&
                result.containsKey("performanceRating")
            }
            .verifyComplete()
    }
    
    @Test
    fun `should check index efficiency`() {
        // When & Then
        StepVerifier.create(mongoIndexService.checkIndexEfficiency())
            .expectNextMatches { efficiencyReports ->
                efficiencyReports.isNotEmpty() &&
                efficiencyReports.all { report ->
                    report.containsKey("indexName") &&
                    report.containsKey("efficiency") &&
                    report.containsKey("recommendation")
                }
            }
            .verifyComplete()
    }
    
    @Test
    fun `should generate optimization recommendations`() {
        // When & Then
        StepVerifier.create(mongoIndexService.generateOptimizationRecommendations())
            .expectNextMatches { recommendations ->
                recommendations.isNotEmpty() &&
                recommendations.all {
                    it.contains("Optimal performance") || it.contains("Performance is acceptable")
                }
            }
            .verifyComplete()
    }
    
    @Test
    fun `should handle performance analysis for different scenarios`() {
        // Test with different room IDs
        val roomIds = listOf(1L, 2L, 999L)
        
        roomIds.forEach { roomId ->
            StepVerifier.create(mongoIndexService.analyzeQueryPerformance(roomId))
                .expectNextMatches { result ->
                    result["roomId"] == roomId &&
                    result.containsKey("performanceRating")
                }
                .verifyComplete()
        }
    }
}