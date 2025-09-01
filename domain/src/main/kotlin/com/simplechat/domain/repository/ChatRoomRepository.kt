package com.simplechat.domain.repository

import com.simplechat.domain.entity.ChatRoom
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDateTime

/**
 * ChatRoom 도메인을 위한 Repository 인터페이스
 */
interface ChatRoomRepository {

    fun findById(id: Long): Mono<ChatRoom>

    fun findAll(): Flux<ChatRoom>

    fun save(domain: ChatRoom): Mono<ChatRoom>

    fun deleteById(id: Long): Mono<Void>

    fun delete(domain: ChatRoom): Mono<Void>

    fun deleteAll(): Mono<Void>

    fun existsById(id: Long): Mono<Boolean>

    fun count(): Mono<Long>

    fun findByName(name: String): Mono<ChatRoom>

    fun findByCreatedBy(userId: Long): Flux<ChatRoom>

    fun findPublicRooms(): Flux<ChatRoom>

    fun findPrivateRooms(): Flux<ChatRoom>

    fun findByNameContaining(namePattern: String): Flux<ChatRoom>

    fun findRecentRooms(limit: Int): Flux<ChatRoom>

    fun findRoomsCreatedAfter(afterDate: LocalDateTime): Flux<ChatRoom>

    fun findActiveRooms(): Flux<ChatRoom>

    fun findAvailablePublicRooms(excludeCreatedBy: Long? = null): Flux<ChatRoom>

    fun existsByName(name: String): Mono<Boolean>

    fun countRooms(): Mono<Long>

    fun countByCreatedBy(userId: Long): Mono<Long>

    fun deleteByCreatedBy(userId: Long): Mono<Void>
}
