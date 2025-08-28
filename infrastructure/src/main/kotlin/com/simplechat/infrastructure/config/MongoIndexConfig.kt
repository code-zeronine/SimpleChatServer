package com.simplechat.infrastructure.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition
import org.springframework.data.mongodb.core.index.Index
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration

/**
 * MongoDB 인덱스 생성 및 최적화 설정
 * 
 * Spring Data MongoDB Reactive 권장 패턴을 따라 구현:
 * - Context7 문서 기반 최적화된 인덱스 생성
 * - Reactive 스트림 체이닝으로 순차적 인덱스 생성
 * - 에러 처리 및 재시도 로직 포함
 * - 인덱스 존재 여부 확인 후 생성
 */
@Component
class MongoIndexConfig(
    private val reactiveMongoTemplate: ReactiveMongoTemplate
) {
    
    private val logger = LoggerFactory.getLogger(MongoIndexConfig::class.java)
    private val collectionName = "chat_messages"
    
    @PostConstruct
    fun createIndexes() {
        logger.info("Starting MongoDB index creation for $collectionName collection...")
        
        createAllIndexes()
            .doOnSuccess { 
                logger.info("All MongoDB indexes created successfully for $collectionName")
            }
            .doOnError { error -> 
                logger.error("Failed to create MongoDB indexes for $collectionName: ${error.message}", error)
            }
            .subscribe()
    }
    
    /**
     * 모든 인덱스를 순차적으로 생성하는 메인 플로우
     * Context7 권장사항: 인덱스 생성을 단일 Mono 체인으로 구성
     */
    private fun createAllIndexes(): Mono<Void> {
        return Flux.fromIterable(getIndexDefinitions())
            .concatMap { indexDef -> 
                createSingleIndex(indexDef)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                    .onErrorResume { error ->
                        logger.warn("Failed to create index ${indexDef.name}: ${error.message}")
                        Mono.empty() // 개별 인덱스 실패가 전체를 중단시키지 않음
                    }
            }
            .then()
            .doOnSubscribe { logger.debug("🔧 Starting index creation process...") }
    }
    
    /**
     * 단일 인덱스 생성 with existence check
     * Context7 권장: 인덱스 존재 여부 확인 후 생성
     */
    private fun createSingleIndex(indexDef: IndexDefinition): Mono<String> {
        return reactiveMongoTemplate.indexOps(collectionName)
            .indexInfo
            .any { existingIndex -> existingIndex.name == indexDef.name }
            .flatMap { exists ->
                if (exists) {
                    logger.debug("📋 Index ${indexDef.name} already exists, skipping...")
                    Mono.just("EXISTS")
                } else {
                    logger.debug("🔨 Creating index: ${indexDef.name}")
                    reactiveMongoTemplate.indexOps(collectionName)
                        .createIndex(indexDef.index)
                        .doOnSuccess { 
                            logger.debug("Successfully created index: ${indexDef.name}")
                        }
                }
            }
    }
    
    /**
     * 인덱스 정의 목록 - Context7 기반 최적화된 구조
     */
    private fun getIndexDefinitions(): List<IndexDefinition> {
        return listOf(
            // 기본 단일 필드 인덱스들
            IndexDefinition(
                "idx_roomId",
                Index().on("roomId", Sort.Direction.ASC)
                    .named("idx_roomId")
            ),
            IndexDefinition(
                "idx_timestamp", 
                Index().on("timestamp", Sort.Direction.DESC)
                    .named("idx_timestamp")
            ),
            IndexDefinition(
                "idx_userId",
                Index().on("userId", Sort.Direction.ASC)
                    .named("idx_userId")
            ),
            IndexDefinition(
                "idx_messageType",
                Index().on("messageType", Sort.Direction.ASC)
                    .named("idx_messageType")
            ),
            
            // 복합 인덱스들 - 쿼리 패턴 최적화
            IndexDefinition(
                "idx_roomId_timestamp_compound",
                CompoundIndexDefinition(
                    org.bson.Document()
                        .append("roomId", 1)
                        .append("timestamp", -1)
                ).named("idx_roomId_timestamp_compound")
            ),
            IndexDefinition(
                "idx_roomId_messageType_timestamp_compound",
                CompoundIndexDefinition(
                    org.bson.Document()
                        .append("roomId", 1)
                        .append("messageType", 1)
                        .append("timestamp", -1)
                ).named("idx_roomId_messageType_timestamp_compound")
            ),
            IndexDefinition(
                "idx_userId_timestamp_compound",
                CompoundIndexDefinition(
                    org.bson.Document()
                        .append("userId", 1)
                        .append("timestamp", -1)
                ).named("idx_userId_timestamp_compound")
            ),
            
            // 최적화 인덱스들
            IndexDefinition(
                "idx_content",
                Index().on("content", Sort.Direction.ASC)
                    .named("idx_content")
            ),
            IndexDefinition(
                "idx_timestamp_range",
                Index().on("timestamp", Sort.Direction.ASC)
                    .named("idx_timestamp_range")
                    .sparse() // null 값 제외
            )
        )
    }
    
    /**
     * 인덱스 정보 조회 및 상태 확인
     * Context7 권장: ReactiveIndexOperations.getIndexInfo() 사용
     */
    fun getIndexInformation(): Mono<List<Map<String, Any>>> {
        return reactiveMongoTemplate.indexOps(collectionName)
            .indexInfo
            .map { indexInfo ->
                mapOf<String, Any>(
                    "name" to (indexInfo.name),
                    "keys" to indexInfo.indexFields.joinToString(", ") { "${it.key}:${it.direction}" },
                    "unique" to indexInfo.isUnique,
                    "sparse" to indexInfo.isSparse
                )
            }
            .collectList()
            .doOnNext { indexes ->
                logger.info("Current indexes for $collectionName: ${indexes.size} total")
                indexes.forEach { index ->
                    logger.debug("   {}: {}", index["name"], index["keys"])
                }
            }
    }
    
    /**
     * 인덱스 성능 분석 - 향후 확장용
     * Context7 참고: 인덱스 사용 통계 모니터링
     */
    fun analyzeIndexPerformance(): Mono<Map<String, Any>> {
        return Mono.fromCallable {
            mapOf<String, Any>(
                "collection" to collectionName,
                "analysisTimestamp" to System.currentTimeMillis(),
                "totalIndexes" to "TBD - requires MongoDB explain() integration"
            )
        }
        .doOnSuccess { analysis ->
            logger.debug("Index performance analysis: {}", analysis)
        }
        .onErrorReturn(
            mapOf<String, Any>(
                "error" to "Index analysis not available",
                "timestamp" to System.currentTimeMillis()
            )
        )
    }
    
    /**
     * 인덱스 정의 데이터 클래스
     */
    private data class IndexDefinition(
        val name: String,
        val index: org.springframework.data.mongodb.core.index.IndexDefinition
    )
}