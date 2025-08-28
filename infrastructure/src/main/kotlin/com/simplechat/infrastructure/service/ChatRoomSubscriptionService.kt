package com.simplechat.infrastructure.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap
import jakarta.annotation.PreDestroy

/**
 * 채팅방 구독 관리 서비스
 * 
 * 사용자별 채팅방 구독을 관리하고 WebSocket 연결과 Redis Pub/Sub를 연동합니다.
 */
@Service
class ChatRoomSubscriptionService(
    private val redisMessageBroker: RedisMessageBrokerService,
    private val sessionCacheService: WebSocketSessionCacheService
) {
    
    private val logger = LoggerFactory.getLogger(ChatRoomSubscriptionService::class.java)
    
    // 사용자별 구독 중인 채팅방 관리
    private val userRoomSubscriptions = ConcurrentHashMap<Long, MutableSet<Long>>()
    
    // 채팅방별 구독자 수 관리
    private val roomSubscriberCounts = ConcurrentHashMap<Long, Int>()
    
    @PreDestroy
    fun destroy() {
        userRoomSubscriptions.clear()
        roomSubscriberCounts.clear()
        logger.info("Chat room subscription service destroyed")
    }
    
    /**
     * 사용자를 채팅방에 구독시킵니다.
     * 
     * @param userId 사용자 ID
     * @param roomId 채팅방 ID
     * @return 구독 성공 여부
     */
    fun subscribeUserToRoom(userId: Long, roomId: Long): Mono<Boolean> {
        return Mono.fromCallable {
            // 사용자의 구독 목록에 채팅방 추가
            val userRooms = userRoomSubscriptions.computeIfAbsent(userId) { mutableSetOf() }
            val isNewSubscription = userRooms.add(roomId)
            
            if (isNewSubscription) {
                // 채팅방 구독자 수 증가
                roomSubscriberCounts.compute(roomId) { _, count -> (count ?: 0) + 1 }
                logger.debug("User {} subscribed to room {}. Room now has {} subscribers", 
                    userId, roomId, roomSubscriberCounts[roomId])
                true
            } else {
                logger.debug("User {} is already subscribed to room {}", userId, roomId)
                false
            }
        }
        .flatMap { isNewSubscription ->
            if (isNewSubscription) {
                // 채팅방 입장 이벤트 발행
                redisMessageBroker.publishRoomEvent(roomId, "USER_JOINED", userId, 
                    mapOf("timestamp" to System.currentTimeMillis()))
                    .map { true }
            } else {
                Mono.just(false)
            }
        }
        .doOnError { error ->
            logger.error("Error subscribing user {} to room {}: {}", userId, roomId, error.message, error)
        }
        .onErrorReturn(false)
    }
    
    /**
     * 사용자의 채팅방 구독을 해제합니다.
     * 
     * @param userId 사용자 ID
     * @param roomId 채팅방 ID
     * @return 구독 해제 성공 여부
     */
    fun unsubscribeUserFromRoom(userId: Long, roomId: Long): Mono<Boolean> {
        return Mono.fromCallable {
            // 사용자의 구독 목록에서 채팅방 제거
            val userRooms = userRoomSubscriptions[userId]
            val wasSubscribed = userRooms?.remove(roomId) ?: false
            
            if (wasSubscribed) {
                // 채팅방 구독자 수 감소
                roomSubscriberCounts.compute(roomId) { _, count -> 
                    val newCount = (count ?: 1) - 1
                    if (newCount <= 0) null else newCount
                }
                
                // 사용자가 더 이상 구독하는 채팅방이 없으면 맵에서 제거
                if (userRooms?.isEmpty() == true) {
                    userRoomSubscriptions.remove(userId)
                }
                
                logger.debug("User {} unsubscribed from room {}. Room now has {} subscribers", 
                    userId, roomId, roomSubscriberCounts[roomId] ?: 0)
                true
            } else {
                logger.debug("User {} was not subscribed to room {}", userId, roomId)
                false
            }
        }
        .flatMap { wasSubscribed ->
            if (wasSubscribed) {
                // 채팅방 퇴장 이벤트 발행
                redisMessageBroker.publishRoomEvent(roomId, "USER_LEFT", userId,
                    mapOf("timestamp" to System.currentTimeMillis()))
                    .map { true }
            } else {
                Mono.just(false)
            }
        }
        .doOnError { error ->
            logger.error("Error unsubscribing user {} from room {}: {}", userId, roomId, error.message, error)
        }
        .onErrorReturn(false)
    }
    
    /**
     * 사용자가 구독 중인 모든 채팅방을 반환합니다.
     * 
     * @param userId 사용자 ID
     * @return 구독 중인 채팅방 ID 목록
     */
    fun getUserSubscribedRooms(userId: Long): Mono<Set<Long>> {
        return Mono.fromCallable {
            userRoomSubscriptions[userId]?.toSet() ?: emptySet()
        }
        .doOnSuccess { rooms ->
            logger.debug("User {} is subscribed to {} rooms: {}", userId, rooms.size, rooms)
        }
    }
    
    /**
     * 채팅방의 구독자 수를 반환합니다.
     * 
     * @param roomId 채팅방 ID
     * @return 구독자 수
     */
    fun getRoomSubscriberCount(roomId: Long): Mono<Int> {
        return Mono.fromCallable {
            roomSubscriberCounts[roomId] ?: 0
        }
        .doOnSuccess { count ->
            logger.debug("Room {} has {} subscribers", roomId, count)
        }
    }
    
    /**
     * 사용자의 모든 채팅방 구독을 해제합니다. (로그아웃 또는 연결 해제 시)
     * 
     * @param userId 사용자 ID
     * @return 해제된 구독 수
     */
    fun unsubscribeUserFromAllRooms(userId: Long): Mono<Int> {
        return getUserSubscribedRooms(userId)
            .flatMapMany { rooms ->
                Flux.fromIterable(rooms)
                    .flatMap { roomId ->
                        unsubscribeUserFromRoom(userId, roomId)
                            .map { if (it) 1 else 0 }
                    }
            }
            .reduce(0) { acc, count -> acc + count }
            .doOnSuccess { unsubscribedCount ->
                logger.info("User {} unsubscribed from {} rooms", userId, unsubscribedCount)
            }
    }
    
    /**
     * 여러 채팅방을 동시에 구독합니다.
     * 
     * @param userId 사용자 ID
     * @param roomIds 채팅방 ID 목록
     * @return 구독된 채팅방 수
     */
    fun subscribeUserToMultipleRooms(userId: Long, roomIds: List<Long>): Mono<Int> {
        return Flux.fromIterable(roomIds)
            .flatMap { roomId ->
                subscribeUserToRoom(userId, roomId)
                    .map { if (it) 1 else 0 }
            }
            .reduce(0) { acc, count -> acc + count }
            .doOnSuccess { subscribedCount ->
                logger.info("User {} subscribed to {} out of {} rooms", 
                    userId, subscribedCount, roomIds.size)
            }
    }
    
    /**
     * 채팅방 구독 통계를 반환합니다.
     * 
     * @return 구독 통계 정보
     */
    fun getSubscriptionStatistics(): Mono<Map<String, Any>> {
        return Mono.fromCallable {
            val totalUsers = userRoomSubscriptions.size
            val totalRooms = roomSubscriberCounts.size
            val totalSubscriptions = userRoomSubscriptions.values.sumOf { it.size }
            val averageRoomsPerUser = if (totalUsers > 0) totalSubscriptions.toDouble() / totalUsers else 0.0
            val averageUsersPerRoom = if (totalRooms > 0) totalSubscriptions.toDouble() / totalRooms else 0.0
            
            mapOf<String, Any>(
                "totalUsers" to totalUsers,
                "totalRooms" to totalRooms,
                "totalSubscriptions" to totalSubscriptions,
                "averageRoomsPerUser" to averageRoomsPerUser,
                "averageUsersPerRoom" to averageUsersPerRoom,
                "timestamp" to System.currentTimeMillis()
            )
        }
        .doOnSuccess { stats ->
            logger.debug("Subscription statistics: {}", stats)
        }
    }
    
    /**
     * 비활성 세션에 대한 구독을 정리합니다.
     * 
     * @return 정리된 구독 수
     */
    fun cleanupInactiveSubscriptions(): Mono<Int> {
        return sessionCacheService.getCacheStatistics()
            .flatMap { stats ->
                Flux.fromIterable(userRoomSubscriptions.keys)
                    .filterWhen { userId ->
                        // 활성 세션이 없는 사용자 찾기
                        sessionCacheService.getUserSessions(userId)
                            .hasElements()
                            .map { hasActiveSession -> !hasActiveSession }
                    }
                    .flatMap { inactiveUserId ->
                        unsubscribeUserFromAllRooms(inactiveUserId)
                    }
                    .reduce(0) { acc, count -> acc + count }
            }
            .doOnSuccess { cleanedUpCount ->
                if (cleanedUpCount > 0) {
                    logger.info("Cleaned up subscriptions for {} inactive users", cleanedUpCount)
                }
            }
            .doOnError { error ->
                logger.error("Error during subscription cleanup: {}", error.message, error)
            }
            .onErrorReturn(0)
    }
}