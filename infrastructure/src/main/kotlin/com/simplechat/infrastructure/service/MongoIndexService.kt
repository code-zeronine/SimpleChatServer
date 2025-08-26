package com.simplechat.infrastructure.service

import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDateTime

/**
 * MongoDB 인덱스 성능 모니터링 및 최적화 서비스
 * 
 * 인덱스 사용 통계, 성능 분석 및 최적화 제안을 제공합니다.
 */
@Service
class MongoIndexService(
    private val reactiveMongoTemplate: ReactiveMongoTemplate
) {
    
    private val logger = LoggerFactory.getLogger(MongoIndexService::class.java)
    private val collectionName = "chat_messages"
    
    /**
     * 모든 인덱스 정보 조회
     */
    fun getAllIndexes(): Flux<Map<String, Any>> {
        return reactiveMongoTemplate.indexOps(collectionName)
            .indexInfo
            .map { indexInfo ->
                mapOf<String, Any>(
                    "name" to (indexInfo.name ?: "unknown"),
                    "keys" to indexInfo.indexFields.toString(),
                    "unique" to indexInfo.isUnique,
                    "sparse" to indexInfo.isSparse
                )
            }
            .doOnNext { index ->
                logger.debug("Index found: ${index["name"]} with keys: ${index["keys"]}")
            }
    }
    
    /**
     * 인덱스 사용 통계 분석 (간소화된 버전)
     */
    fun analyzeIndexUsage(): Mono<Map<String, Any>> {
        return getAllIndexes()
            .collectList()
            .map { indexes ->
                val usageAnalysis = mutableMapOf<String, Any>()
                
                indexes.forEach { indexInfo ->
                    val indexName = indexInfo["name"] as String
                    usageAnalysis[indexName] = mapOf(
                        "operations" to "N/A (requires admin privileges)",
                        "since" to "N/A (requires admin privileges)",
                        "status" to "Available"
                    )
                }
                
                logger.info("Index usage analysis completed for ${indexes.size} indexes")
                usageAnalysis
            }
    }
    
    /**
     * 쿼리 성능 분석
     */
    fun analyzeQueryPerformance(roomId: Long, limit: Int = 50): Mono<Map<String, Any>> {
        val startTime = System.currentTimeMillis()
        
        return reactiveMongoTemplate.find(
            Query.query(
                Criteria.where("roomId").`is`(roomId)
            ).limit(limit)
                .with(Sort.by("timestamp").descending()),
            org.bson.Document::class.java,
            collectionName
        ).collectList()
            .map { results ->
                val endTime = System.currentTimeMillis()
                val executionTime = endTime - startTime
                
                mapOf<String, Any>(
                    "queryType" to "roomId_with_timestamp_sort",
                    "executionTimeMs" to executionTime,
                    "resultsCount" to results.size,
                    "roomId" to roomId,
                    "limit" to limit,
                    "performanceRating" to when {
                        executionTime < 10 -> "EXCELLENT"
                        executionTime < 50 -> "GOOD"
                        executionTime < 200 -> "AVERAGE"
                        else -> "POOR"
                    }
                )
            }
            .doOnNext { analysis ->
                logger.info("Query performance analysis: ${analysis["performanceRating"]} (${analysis["executionTimeMs"]}ms)")
            }
    }
    
    /**
     * 인덱스 효율성 체크
     */
    fun checkIndexEfficiency(): Mono<List<Map<String, Any>>> {
        return getAllIndexes()
            .flatMap { indexInfo ->
                val indexName = indexInfo["name"] as String
                
                // 각 인덱스에 대한 효율성 테스트
                when {
                    indexName.contains("roomId") -> testRoomIdIndexEfficiency()
                    indexName.contains("timestamp") -> testTimestampIndexEfficiency()
                    indexName.contains("userId") -> testUserIdIndexEfficiency()
                    else -> Mono.just(mapOf(
                        "indexName" to indexName,
                        "efficiency" to "UNKNOWN",
                        "recommendation" to "Manual analysis required"
                    ))
                }
            }
            .collectList()
            .doOnNext { efficiencyReports ->
                logger.info("Index efficiency check completed for ${efficiencyReports.size} indexes")
            }
    }
    
    /**
     * roomId 인덱스 효율성 테스트
     */
    private fun testRoomIdIndexEfficiency(): Mono<Map<String, Any>> {
        val testRoomId = 1L
        val startTime = System.currentTimeMillis()
        
        return reactiveMongoTemplate.count(
            Query.query(
                Criteria.where("roomId").`is`(testRoomId)
            ),
            collectionName
        ).map { count ->
            val executionTime = System.currentTimeMillis() - startTime
            
            mapOf(
                "indexName" to "roomId_indexes",
                "testType" to "COUNT_BY_ROOM_ID",
                "executionTimeMs" to executionTime,
                "recordsFound" to count,
                "efficiency" to when {
                    executionTime < 5 -> "EXCELLENT"
                    executionTime < 20 -> "GOOD"
                    executionTime < 100 -> "AVERAGE"
                    else -> "POOR"
                },
                "recommendation" to if (executionTime > 50) {
                    "Consider optimizing roomId index or collection size"
                } else {
                    "Index performance is acceptable"
                }
            )
        }
    }
    
    /**
     * timestamp 인덱스 효율성 테스트
     */
    private fun testTimestampIndexEfficiency(): Mono<Map<String, Any>> {
        val startTime = System.currentTimeMillis()
        val since = LocalDateTime.now().minusDays(1)
        
        return reactiveMongoTemplate.count(
            Query.query(
                Criteria.where("timestamp").gte(since)
            ),
            collectionName
        ).map { count ->
            val executionTime = System.currentTimeMillis() - startTime
            
            mapOf(
                "indexName" to "timestamp_indexes",
                "testType" to "COUNT_BY_TIMESTAMP_RANGE",
                "executionTimeMs" to executionTime,
                "recordsFound" to count,
                "efficiency" to when {
                    executionTime < 10 -> "EXCELLENT"
                    executionTime < 30 -> "GOOD"
                    executionTime < 150 -> "AVERAGE"
                    else -> "POOR"
                },
                "recommendation" to if (executionTime > 100) {
                    "Consider compound index optimization for timestamp queries"
                } else {
                    "Timestamp index performance is acceptable"
                }
            )
        }
    }
    
    /**
     * userId 인덱스 효율성 테스트
     */
    private fun testUserIdIndexEfficiency(): Mono<Map<String, Any>> {
        val testUserId = 100L
        val startTime = System.currentTimeMillis()
        
        return reactiveMongoTemplate.count(
            Query.query(
                Criteria.where("userId").`is`(testUserId)
            ),
            collectionName
        ).map { count ->
            val executionTime = System.currentTimeMillis() - startTime
            
            mapOf(
                "indexName" to "userId_indexes",
                "testType" to "COUNT_BY_USER_ID",
                "executionTimeMs" to executionTime,
                "recordsFound" to count,
                "efficiency" to when {
                    executionTime < 8 -> "EXCELLENT"
                    executionTime < 25 -> "GOOD"
                    executionTime < 120 -> "AVERAGE"
                    else -> "POOR"
                },
                "recommendation" to if (executionTime > 80) {
                    "Consider userId index optimization or data distribution analysis"
                } else {
                    "UserId index performance is acceptable"
                }
            )
        }
    }
    
    /**
     * 인덱스 최적화 제안 생성
     */
    fun generateOptimizationRecommendations(): Mono<List<String>> {
        return checkIndexEfficiency()
            .map { efficiencyReports ->
                val recommendations = mutableListOf<String>()
                
                efficiencyReports.forEach { report ->
                    val efficiency = report["efficiency"] as String
                    val indexName = report["indexName"] as String
                    
                    when (efficiency) {
                        "POOR" -> {
                            recommendations.add("🔴 $indexName: Immediate optimization required")
                        }
                        "AVERAGE" -> {
                            recommendations.add("🟡 $indexName: Consider performance tuning")
                        }
                        "GOOD" -> {
                            recommendations.add("🟢 $indexName: Performance is acceptable")
                        }
                        "EXCELLENT" -> {
                            recommendations.add("✅ $indexName: Optimal performance")
                        }
                    }
                }
                
                if (recommendations.isEmpty()) {
                    recommendations.add("No specific optimization recommendations at this time")
                }
                
                logger.info("Generated ${recommendations.size} optimization recommendations")
                recommendations
            }
    }
}