package com.simplechat.infrastructure.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition
import org.springframework.data.mongodb.core.index.Index
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * MongoDB 인덱스 생성 및 최적화 설정
 * 
 * 애플리케이션 시작시 필요한 인덱스들을 자동으로 생성하고
 * 채팅 메시지 조회 성능을 최적화합니다.
 */
@Component
class MongoIndexConfig(
    private val reactiveMongoTemplate: ReactiveMongoTemplate
) {
    
    private val logger = LoggerFactory.getLogger(MongoIndexConfig::class.java)
    private val collectionName = "chat_messages"
    
    @PostConstruct
    fun createIndexes() {
        logger.info("Creating MongoDB indexes for chat_messages collection...")
        
        createBasicIndexes()
            .then(createCompoundIndexes())
            .then(createOptimizedIndexes())
            .subscribe(
                { logger.info("All MongoDB indexes created successfully") },
                { error -> logger.error("Failed to create MongoDB indexes", error) }
            )
    }
    
    /**
     * 기본 단일 필드 인덱스 생성
     */
    private fun createBasicIndexes(): Mono<Void> {
        return Mono.fromRunnable {
            logger.debug("Creating basic indexes...")
            
            // roomId 인덱스 (가장 자주 사용되는 쿼리 필드)
            reactiveMongoTemplate.indexOps(collectionName)
                .ensureIndex(Index().on("roomId", Sort.Direction.ASC).named("idx_roomId"))
                .subscribe { logger.debug("Created roomId index") }
            
            // timestamp 인덱스 (정렬용)
            reactiveMongoTemplate.indexOps(collectionName)
                .ensureIndex(Index().on("timestamp", Sort.Direction.DESC).named("idx_timestamp"))
                .subscribe { logger.debug("Created timestamp index") }
            
            // userId 인덱스 (사용자별 메시지 조회용)
            reactiveMongoTemplate.indexOps(collectionName)
                .ensureIndex(Index().on("userId", Sort.Direction.ASC).named("idx_userId"))
                .subscribe { logger.debug("Created userId index") }
            
            // messageType 인덱스 (타입별 필터링용)
            reactiveMongoTemplate.indexOps(collectionName)
                .ensureIndex(Index().on("messageType", Sort.Direction.ASC).named("idx_messageType"))
                .subscribe { logger.debug("Created messageType index") }
        }
    }
    
    /**
     * 복합 인덱스 생성 (성능 최적화)
     */
    private fun createCompoundIndexes(): Mono<Void> {
        return Mono.fromRunnable {
            logger.debug("Creating compound indexes...")
            
            // roomId + timestamp 복합 인덱스 (채팅방별 시간순 정렬)
            val roomIdTimestampIndex = CompoundIndexDefinition(
                org.bson.Document()
                    .append("roomId", 1)
                    .append("timestamp", -1)
            ).named("idx_roomId_timestamp_compound")
            
            reactiveMongoTemplate.indexOps(collectionName)
                .ensureIndex(roomIdTimestampIndex)
                .subscribe { logger.debug("Created roomId + timestamp compound index") }
            
            // roomId + messageType + timestamp 복합 인덱스 (타입별 메시지 조회)
            val roomIdTypeTimestampIndex = CompoundIndexDefinition(
                org.bson.Document()
                    .append("roomId", 1)
                    .append("messageType", 1)
                    .append("timestamp", -1)
            ).named("idx_roomId_messageType_timestamp_compound")
            
            reactiveMongoTemplate.indexOps(collectionName)
                .ensureIndex(roomIdTypeTimestampIndex)
                .subscribe { logger.debug("Created roomId + messageType + timestamp compound index") }
            
            // userId + timestamp 복합 인덱스 (사용자별 메시지 시간순)
            val userIdTimestampIndex = CompoundIndexDefinition(
                org.bson.Document()
                    .append("userId", 1)
                    .append("timestamp", -1)
            ).named("idx_userId_timestamp_compound")
            
            reactiveMongoTemplate.indexOps(collectionName)
                .ensureIndex(userIdTimestampIndex)
                .subscribe { logger.debug("Created userId + timestamp compound index") }
        }
    }
    
    /**
     * 추가 최적화 인덱스 생성
     */
    private fun createOptimizedIndexes(): Mono<Void> {
        return Mono.fromRunnable {
            logger.debug("Creating optimized indexes...")
            
            // 텍스트 검색을 위한 content 인덱스 (향후 메시지 검색 기능용)
            reactiveMongoTemplate.indexOps(collectionName)
                .ensureIndex(Index().on("content", Sort.Direction.ASC).named("idx_content"))
                .subscribe { logger.debug("Created content index for text search") }
            
            // 시간 범위 쿼리 최적화를 위한 timestamp 범위 인덱스
            val timestampRangeIndex = Index().on("timestamp", Sort.Direction.ASC)
                .named("idx_timestamp_range")
                .sparse() // null 값 제외
            
            reactiveMongoTemplate.indexOps(collectionName)
                .ensureIndex(timestampRangeIndex)
                .subscribe { logger.debug("Created timestamp range index") }
        }
    }
    
    /**
     * 인덱스 생성 상태 확인 및 로깅
     */
    fun logIndexInformation() {
        reactiveMongoTemplate.indexOps(collectionName)
            .indexInfo
            .doOnNext { indexInfo ->
                logger.info("Index: ${indexInfo.name} - Keys: ${indexInfo.indexFields}")
            }
            .subscribe()
    }
}