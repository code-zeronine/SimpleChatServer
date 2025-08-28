package com.simplechat.infrastructure.service

import com.simplechat.domain.entity.ChatMessage
import org.slf4j.LoggerFactory
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import java.util.concurrent.ConcurrentHashMap
import jakarta.annotation.PreDestroy

/**
 * 채팅방 구독 관리 서비스
 * 
 * 사용자의 채팅방 입장/퇴장에 따른 Redis 채널 구독/해제를 관리합니다.
 * 사용자별 구독 상태를 추적하고 자동 정리 기능을 제공합니다.
 */
@Service
class RoomSubscriptionService(
    private val channelLifecycleService: RedisChannelLifecycleService,
    private val redisSubscriptionService: RedisSubscriptionService,
    private val redisPubSubService: RedisPubSubService,
    private val messageSerializationService: RedisMessageSerializationService
) {
    
    private val logger = LoggerFactory.getLogger(RoomSubscriptionService::class.java)
    
    // 사용자별 구독 정보 추적
    private val userSubscriptions = ConcurrentHashMap<Long, MutableSet<UserSubscription>>()
    
    // 채팅방별 구독자 추적
    private val roomSubscribers = ConcurrentHashMap<Long, MutableSet<Long>>()
    
    // 구독 이벤트 스트림
    private val subscriptionEventSink = Sinks.many().multicast().onBackpressureBuffer<SubscriptionEvent>()
    
    @PreDestroy
    fun cleanup() {
        // 모든 구독 해제
        userSubscriptions.values.forEach { subscriptions ->
            subscriptions.forEach { subscription ->
                if (!subscription.disposable.isDisposed) {
                    subscription.disposable.dispose()
                }
            }
        }
        
        userSubscriptions.clear()
        roomSubscribers.clear()
        subscriptionEventSink.tryEmitComplete()
        
        logger.info("Room subscription service cleaned up")
    }
    
    /**
     * 사용자가 채팅방에 입장할 때 채널을 구독합니다.
     * 
     * @param userId 사용자 ID
     * @param roomId 채팅방 ID
     * @return 채팅 메시지 스트림
     */
    fun joinRoom(userId: Long, roomId: Long): Mono<Flux<ChatMessage>> {
        return channelLifecycleService.createRoomChannel(roomId)
            .flatMap { channelTopic ->
                subscribeToRoomChannel(userId, roomId, channelTopic)
            }
            .doOnSuccess { 
                logger.debug("User {} joined room {}", userId, roomId)
            }
            .doOnError { error ->
                logger.error("Failed to join user {} to room {}: {}", 
                    userId, roomId, error.message, error)
            }
    }
    
    /**
     * 사용자가 채팅방에서 퇴장할 때 채널 구독을 해제합니다.
     * 
     * @param userId 사용자 ID
     * @param roomId 채팅방 ID
     * @return 퇴장 성공 여부
     */
    fun leaveRoom(userId: Long, roomId: Long): Mono<Boolean> {
        return Mono.fromCallable {
            // 사용자 구독 정보에서 해당 방 구독 찾기
            val userSubs = userSubscriptions[userId]
            val subscription = userSubs?.find { it.roomId == roomId }
            
            if (subscription != null) {
                // 구독 해제
                if (!subscription.disposable.isDisposed) {
                    subscription.disposable.dispose()
                }
                
                // 구독 정보에서 제거
                userSubs.remove(subscription)
                if (userSubs.isEmpty()) {
                    userSubscriptions.remove(userId)
                }
                
                // 채팅방 구독자에서 제거
                roomSubscribers[roomId]?.remove(userId)
                if (roomSubscribers[roomId]?.isEmpty() == true) {
                    roomSubscribers.remove(roomId)
                    // 구독자가 없으면 채널 비활성화
                    channelLifecycleService.deactivateChannel(
                        "chat:room:$roomId"
                    ).subscribe()
                }
                
                // 구독자 수 업데이트
                val remainingSubscribers = roomSubscribers[roomId]?.size ?: 0
                channelLifecycleService.updateSubscriberCount(
                    "chat:room:$roomId", remainingSubscribers
                ).subscribe()
                
                // 퇴장 이벤트 발행
                emitSubscriptionEvent(SubscriptionEvent(
                    type = SubscriptionEventType.LEFT_ROOM,
                    userId = userId,
                    roomId = roomId,
                    subscriberCount = remainingSubscribers,
                    timestamp = System.currentTimeMillis()
                ))
                
                // 퇴장 메시지 발행
                publishLeaveMessage(userId, roomId).subscribe()
                
                logger.debug("User {} left room {} (remaining subscribers: {})", 
                    userId, roomId, remainingSubscribers)
                true
            } else {
                logger.warn("User {} was not subscribed to room {}", userId, roomId)
                false
            }
        }
    }
    
    /**
     * 사용자가 구독 중인 모든 채팅방에서 퇴장합니다.
     * 
     * @param userId 사용자 ID
     * @return 퇴장한 채팅방 수
     */
    fun leaveAllRooms(userId: Long): Mono<Int> {
        return Mono.fromCallable {
            val userSubs = userSubscriptions[userId] ?: return@fromCallable 0
            val roomIds = userSubs.map { it.roomId }.toList()
            
            var leftRooms = 0
            roomIds.forEach { roomId ->
                leaveRoom(userId, roomId).subscribe { success ->
                    if (success) leftRooms++
                }
            }
            
            logger.info("User {} left {} rooms", userId, leftRooms)
            leftRooms
        }
    }
    
    /**
     * 사용자가 구독 중인 채팅방 목록을 조회합니다.
     * 
     * @param userId 사용자 ID
     * @return 구독 중인 채팅방 ID 목록
     */
    fun getUserSubscribedRooms(userId: Long): List<Long> {
        return userSubscriptions[userId]?.map { it.roomId } ?: emptyList()
    }
    
    /**
     * 채팅방의 구독자 목록을 조회합니다.
     * 
     * @param roomId 채팅방 ID
     * @return 구독자 사용자 ID 목록
     */
    fun getRoomSubscribers(roomId: Long): List<Long> {
        return roomSubscribers[roomId]?.toList() ?: emptyList()
    }
    
    /**
     * 채팅방의 구독자 수를 조회합니다.
     * 
     * @param roomId 채팅방 ID
     * @return 구독자 수
     */
    fun getRoomSubscriberCount(roomId: Long): Int {
        return roomSubscribers[roomId]?.size ?: 0
    }
    
    /**
     * 사용자가 특정 채팅방을 구독 중인지 확인합니다.
     * 
     * @param userId 사용자 ID
     * @param roomId 채팅방 ID
     * @return 구독 여부
     */
    fun isUserSubscribedToRoom(userId: Long, roomId: Long): Boolean {
        return userSubscriptions[userId]?.any { it.roomId == roomId } ?: false
    }
    
    /**
     * 구독 이벤트 스트림을 제공합니다.
     * 
     * @return 구독 이벤트 스트림
     */
    fun subscribeToSubscriptionEvents(): Flux<SubscriptionEvent> {
        return subscriptionEventSink.asFlux()
            .doOnSubscribe { 
                logger.debug("New subscriber to room subscription events")
            }
    }
    
    /**
     * 전체 구독 통계를 조회합니다.
     * 
     * @return 구독 통계 정보
     */
    fun getSubscriptionStatistics(): Mono<SubscriptionStatistics> {
        return Mono.fromCallable {
            val totalUsers = userSubscriptions.size
            val totalRooms = roomSubscribers.size
            val totalSubscriptions = userSubscriptions.values.sumOf { it.size }
            val averageRoomsPerUser = if (totalUsers > 0) totalSubscriptions.toDouble() / totalUsers else 0.0
            val averageUsersPerRoom = if (totalRooms > 0) totalSubscriptions.toDouble() / totalRooms else 0.0
            
            SubscriptionStatistics(
                totalUsers = totalUsers,
                totalRooms = totalRooms,
                totalSubscriptions = totalSubscriptions,
                averageRoomsPerUser = averageRoomsPerUser,
                averageUsersPerRoom = averageUsersPerRoom,
                timestamp = System.currentTimeMillis()
            )
        }
    }
    
    /**
     * 채팅방 채널을 구독합니다.
     * 
     * @param userId 사용자 ID
     * @param roomId 채팅방 ID
     * @param channelTopic 채널 토픽
     * @return 채팅 메시지 스트림
     */
    private fun subscribeToRoomChannel(
        userId: Long, 
        roomId: Long, 
        channelTopic: ChannelTopic
    ): Mono<Flux<ChatMessage>> {
        
        return Mono.fromCallable {
            val messageStream = redisSubscriptionService.subscribeToRoom(roomId)
            
            val subscription = UserSubscription(
                userId = userId,
                roomId = roomId,
                channelName = channelTopic.topic,
                subscribedAt = System.currentTimeMillis(),
                disposable = messageStream.subscribe()
            )
            
            // 사용자 구독 정보에 추가
            userSubscriptions.computeIfAbsent(userId) { mutableSetOf() }
                .add(subscription)
            
            // 채팅방 구독자에 추가
            roomSubscribers.computeIfAbsent(roomId) { mutableSetOf() }
                .add(userId)
            
            // 구독자 수 업데이트
            val subscriberCount = roomSubscribers[roomId]?.size ?: 0
            channelLifecycleService.updateSubscriberCount(
                channelTopic.topic, subscriberCount
            ).subscribe()
            
            // 입장 이벤트 발행
            emitSubscriptionEvent(SubscriptionEvent(
                type = SubscriptionEventType.JOINED_ROOM,
                userId = userId,
                roomId = roomId,
                subscriberCount = subscriberCount,
                timestamp = System.currentTimeMillis()
            ))
            
            // 입장 메시지 발행
            publishJoinMessage(userId, roomId).subscribe()
            
            messageStream
        }
    }
    
    /**
     * 입장 메시지를 발행합니다.
     * 
     * @param userId 사용자 ID
     * @param roomId 채팅방 ID
     * @return 발행 결과
     */
    private fun publishJoinMessage(userId: Long, roomId: Long): Mono<Long> {
        return messageSerializationService.createRoomEvent(
            roomId = roomId,
            eventType = "USER_JOINED",
            userId = userId,
            additionalData = mapOf(
                "subscriberCount" to getRoomSubscriberCount(roomId)
            )
        ).flatMap { jsonMessage ->
            redisPubSubService.publishToUser(userId, jsonMessage)
        }
    }
    
    /**
     * 퇴장 메시지를 발행합니다.
     * 
     * @param userId 사용자 ID
     * @param roomId 채팅방 ID
     * @return 발행 결과
     */
    private fun publishLeaveMessage(userId: Long, roomId: Long): Mono<Long> {
        return messageSerializationService.createRoomEvent(
            roomId = roomId,
            eventType = "USER_LEFT",
            userId = userId,
            additionalData = mapOf(
                "subscriberCount" to getRoomSubscriberCount(roomId)
            )
        ).flatMap { jsonMessage ->
            redisPubSubService.publishToUser(userId, jsonMessage)
        }
    }
    
    /**
     * 구독 이벤트를 발행합니다.
     * 
     * @param event 구독 이벤트
     */
    private fun emitSubscriptionEvent(event: SubscriptionEvent) {
        val result = subscriptionEventSink.tryEmitNext(event)
        if (result.isFailure) {
            logger.warn("Failed to emit subscription event: {} for user {} room {}", 
                result, event.userId, event.roomId)
        }
    }
}

/**
 * 사용자 구독 정보 데이터 클래스
 */
data class UserSubscription(
    val userId: Long,
    val roomId: Long,
    val channelName: String,
    val subscribedAt: Long,
    val disposable: reactor.core.Disposable
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserSubscription) return false
        return userId == other.userId && roomId == other.roomId
    }
    
    override fun hashCode(): Int {
        return 31 * userId.hashCode() + roomId.hashCode()
    }
}

/**
 * 구독 이벤트 데이터 클래스
 */
data class SubscriptionEvent(
    val type: SubscriptionEventType,
    val userId: Long,
    val roomId: Long,
    val subscriberCount: Int,
    val timestamp: Long
)

/**
 * 구독 이벤트 타입 열거형
 */
enum class SubscriptionEventType {
    JOINED_ROOM,    // 채팅방 입장
    LEFT_ROOM,      // 채팅방 퇴장
    RECONNECTED,    // 재연결
    DISCONNECTED    // 연결 끊김
}

/**
 * 구독 통계 정보 데이터 클래스
 */
data class SubscriptionStatistics(
    val totalUsers: Int,
    val totalRooms: Int,
    val totalSubscriptions: Int,
    val averageRoomsPerUser: Double,
    val averageUsersPerRoom: Double,
    val timestamp: Long
)