package com.simplechat.domain.repository

import com.simplechat.domain.entity.ChatRoomRole
import com.simplechat.domain.entity.UserChatRoom
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDateTime

/**
 * User-ChatRoom 관계를 위한 Repository 인터페이스
 */
interface UserChatRoomRepository {

    fun save(userChatRoom: UserChatRoom): Mono<UserChatRoom>

    fun saveAll(userChatRooms: Iterable<UserChatRoom>): Flux<UserChatRoom>

    fun update(userChatRoom: UserChatRoom): Mono<UserChatRoom>

    fun findByUserIdAndChatRoomId(userId: Long, chatRoomId: Long): Mono<UserChatRoom>

    fun findByUserId(userId: Long): Flux<UserChatRoom>

    fun findActiveRoomsByUserId(userId: Long): Flux<UserChatRoom>

    fun findByChatRoomId(chatRoomId: Long): Flux<UserChatRoom>

    fun findActiveParticipantsByChatRoomId(chatRoomId: Long): Flux<UserChatRoom>

    fun findByChatRoomIdAndRole(chatRoomId: Long, role: ChatRoomRole): Flux<UserChatRoom>

    fun findOwnerByChatRoomId(chatRoomId: Long): Mono<UserChatRoom>

    fun findAdminsByChatRoomId(chatRoomId: Long): Flux<UserChatRoom>

    fun findPinnedRoomsByUserId(userId: Long): Flux<UserChatRoom>

    fun findMutedRoomsByUserId(userId: Long): Flux<UserChatRoom>

    fun findUnreadRoomsByUserId(userId: Long): Flux<UserChatRoom>

    fun findByInvitedBy(invitedBy: Long): Flux<UserChatRoom>

    fun findRecentRoomsByUserId(userId: Long, limit: Int): Flux<UserChatRoom>

    fun findByJoinedAfter(afterDate: LocalDateTime): Flux<UserChatRoom>

    fun findLeftParticipantsByChatRoomId(chatRoomId: Long): Flux<UserChatRoom>

    fun existsByUserIdAndChatRoomId(userId: Long, chatRoomId: Long): Mono<Boolean>

    fun isActiveParticipant(userId: Long, chatRoomId: Long): Mono<Boolean>

    fun countActiveParticipantsByChatRoomId(chatRoomId: Long): Mono<Long>

    fun countActiveRoomsByUserId(userId: Long): Mono<Long>

    fun countByRole(role: ChatRoomRole): Mono<Long>

    fun countByChatRoomId(chatRoomId: Long): Mono<Long>

    fun delete(userChatRoom: UserChatRoom): Mono<Void>

    fun deleteByUserIdAndChatRoomId(userId: Long, chatRoomId: Long): Mono<Void>

    fun deleteByChatRoomId(chatRoomId: Long): Mono<Void>

    fun deleteByUserId(userId: Long): Mono<Void>

    fun deleteAll(): Mono<Void>
}
