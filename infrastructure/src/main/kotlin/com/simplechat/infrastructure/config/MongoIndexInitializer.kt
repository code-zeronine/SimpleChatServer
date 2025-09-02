package com.simplechat.infrastructure.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.index.TextIndexDefinition
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration

/**
 * MongoDB 인덱스 초기화 클래스
 * 
 * 순환 의존성을 방지하기 위해 인덱스 생성 로직을 분리했습니다.
 * 애플리케이션 시작 시 필요한 MongoDB 인덱스를 생성합니다.
 */
@Component
class MongoIndexInitializer(
    private val reactiveMongoTemplate: ReactiveMongoTemplate
) {
    
    private val logger = LoggerFactory.getLogger(MongoIndexInitializer::class.java)
    
    @PostConstruct
    fun createMongoIndexes() {
        logger.info("Starting MongoDB index creation for chat_messages collection...")
        
        createAllIndexes()
            .doOnSuccess { 
                logger.info("All MongoDB indexes created successfully for chat_messages")
            }
            .doOnError { error -> 
                logger.error("Failed to create MongoDB indexes for chat_messages: ${error.message}", error)
            }
            .subscribe()
    }

    private fun createAllIndexes(): Mono<Void> {
        return Flux.fromIterable(getIndexDefinitions())
            .concatMap { indexDef -> 
                createSingleIndex(indexDef)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(1)))
                    .onErrorResume { error ->
                        logger.warn("Failed to create index ${indexDef.name}: ${error.message}")
                        Mono.empty()
                    }
            }
            .then()
    }

    private fun createSingleIndex(indexDef: IndexDefinition): Mono<String> {
        return reactiveMongoTemplate.indexOps("chat_messages")
            .indexInfo
            .any { existingIndex -> existingIndex.name == indexDef.name }
            .flatMap { exists ->
                if (exists) {
                    logger.debug("Index ${indexDef.name} already exists, skipping...")
                    Mono.just("EXISTS")
                } else {
                    logger.debug("Creating index: ${indexDef.name}")
                    reactiveMongoTemplate.indexOps("chat_messages")
                        .createIndex(indexDef.index)
                        .doOnSuccess { 
                            logger.debug("Successfully created index: ${indexDef.name}")
                        }
                        .map { "CREATED" }
                }
            }
    }

    private fun getIndexDefinitions(): List<IndexDefinition> {
        return listOf(
            // 1. 채팅방별 메시지 조회용 인덱스
            IndexDefinition(
                "idx_roomId",
                Index().on("roomId", Sort.Direction.ASC).named("idx_roomId")
            ),
            
            // 2. 시간순 메시지 조회용 인덱스
            IndexDefinition(
                "idx_timestamp", 
                Index().on("timestamp", Sort.Direction.DESC).named("idx_timestamp")
            ),
            
            // 3. 사용자별 메시지 조회용 인덱스
            IndexDefinition(
                "idx_userId",
                Index().on("userId", Sort.Direction.ASC).named("idx_userId")
            ),
            
            // 4. 메시지 타입별 조회용 인덱스
            IndexDefinition(
                "idx_messageType",
                Index().on("messageType", Sort.Direction.ASC).named("idx_messageType")
            ),
            
            // 5. 채팅방별 시간순 복합 인덱스 (가장 중요)
            IndexDefinition(
                "idx_roomId_timestamp_compound",
                CompoundIndexDefinition(
                    org.bson.Document()
                        .append("roomId", 1)
                        .append("timestamp", -1)
                ).named("idx_roomId_timestamp_compound")
            ),
            
            // 6. 채팅방별 메시지 타입 시간순 복합 인덱스
            IndexDefinition(
                "idx_roomId_messageType_timestamp_compound",
                CompoundIndexDefinition(
                    org.bson.Document()
                        .append("roomId", 1)
                        .append("messageType", 1)
                        .append("timestamp", -1)
                ).named("idx_roomId_messageType_timestamp_compound")
            ),
            
            // 7. 사용자별 시간순 복합 인덱스
            IndexDefinition(
                "idx_userId_timestamp_compound",
                CompoundIndexDefinition(
                    org.bson.Document()
                        .append("userId", 1)
                        .append("timestamp", -1)
                ).named("idx_userId_timestamp_compound")
            ),
            
            // 8. 텍스트 검색용 인덱스
            IndexDefinition(
                "idx_content_text",
                TextIndexDefinition.builder()
                    .onField("content")
                    .named("idx_content_text")
                    .build()
            )
        )
    }

    private data class IndexDefinition(
        val name: String,
        val index: org.springframework.data.mongodb.core.index.IndexDefinition
    )
}