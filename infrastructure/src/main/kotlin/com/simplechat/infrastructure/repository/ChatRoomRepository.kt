package com.simplechat.infrastructure.repository

import com.simplechat.domain.entity.ChatRoom
import com.simplechat.infrastructure.entity.ChatRoomEntity
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.relational.core.query.Criteria
import org.springframework.data.relational.core.query.Query
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDateTime

/**
 * ChatRoom 도메인을 위한 Repository 구현체
 * 
 * R2dbcEntityTemplate을 직접 사용하여 도메인 객체를 반환합니다.
 * ChatRoomEntity는 내부 구현 디테일로만 사용됩니다.
 */
@Repository
class ChatRoomRepository(
    private val template: R2dbcEntityTemplate
) {

    /**
     * 채팅방을 저장합니다. (신규 생성 또는 업데이트)
     */
    fun save(chatRoom: ChatRoom): Mono<ChatRoom> {
        val chatRoomEntity = ChatRoomEntity.fromDomain(chatRoom)
        return if (chatRoom.id == null) {
            // 신규 채팅방 생성
            template.insert(chatRoomEntity).map { it.toDomain() }
        } else {
            // 기존 채팅방 업데이트
            template.update(chatRoomEntity).map { it.toDomain() }
        }
    }

    /**
     * 여러 채팅방을 저장합니다.
     */
    fun saveAll(chatRooms: Iterable<ChatRoom>): Flux<ChatRoom> {
        return Flux.fromIterable(chatRooms)
            .flatMap { save(it) }
    }

    /**
     * ID로 채팅방을 조회합니다.
     */
    fun findById(id: Long): Mono<ChatRoom> {
        return template.selectOne(
            Query.query(Criteria.where("id").`is`(id)),
            ChatRoomEntity::class.java
        ).map { it.toDomain() }
    }

    /**
     * 채팅방 이름으로 조회합니다.
     */
    fun findByName(name: String): Mono<ChatRoom> {
        return template.selectOne(
            Query.query(Criteria.where("name").`is`(name)),
            ChatRoomEntity::class.java
        ).map { it.toDomain() }
    }

    /**
     * 특정 사용자가 생성한 채팅방들을 조회합니다.
     */
    fun findByCreatedBy(userId: Long): Flux<ChatRoom> {
        return template.select(
            Query.query(Criteria.where("created_by").`is`(userId)),
            ChatRoomEntity::class.java
        ).map { it.toDomain() }
    }

    /**
     * 공개 채팅방들을 조회합니다.
     */
    fun findPublicRooms(): Flux<ChatRoom> {
        return template.select(
            Query.query(Criteria.where("is_private").`is`(false)),
            ChatRoomEntity::class.java
        ).map { it.toDomain() }
    }

    /**
     * 비공개 채팅방들을 조회합니다.
     */
    fun findPrivateRooms(): Flux<ChatRoom> {
        return template.select(
            Query.query(Criteria.where("is_private").`is`(true)),
            ChatRoomEntity::class.java
        ).map { it.toDomain() }
    }

    /**
     * 채팅방 이름으로 검색합니다. (부분 일치)
     */
    fun findByNameContaining(namePattern: String): Flux<ChatRoom> {
        return template.getDatabaseClient()
            .sql("SELECT * FROM chat_rooms WHERE name ILIKE :pattern ORDER BY created_at DESC")
            .bind("pattern", "%$namePattern%")
            .map { row, _ -> mapRowToChatRoomEntity(row).toDomain() }
            .all()
    }

    /**
     * 최근 생성된 채팅방들을 조회합니다.
     */
    fun findRecentRooms(limit: Int): Flux<ChatRoom> {
        return template.getDatabaseClient()
            .sql("SELECT * FROM chat_rooms ORDER BY created_at DESC LIMIT :limit")
            .bind("limit", limit)
            .map { row, _ -> mapRowToChatRoomEntity(row).toDomain() }
            .all()
    }

    /**
     * 특정 일자 이후에 생성된 채팅방들을 조회합니다.
     */
    fun findRoomsCreatedAfter(afterDate: LocalDateTime): Flux<ChatRoom> {
        return template.getDatabaseClient()
            .sql("SELECT * FROM chat_rooms WHERE created_at >= :afterDate ORDER BY created_at")
            .bind("afterDate", afterDate)
            .map { row, _ -> mapRowToChatRoomEntity(row).toDomain() }
            .all()
    }

    /**
     * 활성 채팅방들을 조회합니다. (최근 24시간 내 업데이트된 방)
     */
    fun findActiveRooms(): Flux<ChatRoom> {
        val twentyFourHoursAgo = LocalDateTime.now().minusHours(24)
        return template.getDatabaseClient()
            .sql("SELECT * FROM chat_rooms WHERE updated_at >= :since ORDER BY updated_at DESC")
            .bind("since", twentyFourHoursAgo)
            .map { row, _ -> mapRowToChatRoomEntity(row).toDomain() }
            .all()
    }

    /**
     * 특정 사용자가 참여 가능한 공개 채팅방들을 조회합니다.
     */
    fun findAvailablePublicRooms(excludeCreatedBy: Long? = null): Flux<ChatRoom> {
        return if (excludeCreatedBy != null) {
            template.select(
                Query.query(
                    Criteria.where("is_private").`is`(false)
                        .and("created_by").not(excludeCreatedBy)
                ),
                ChatRoomEntity::class.java
            ).map { it.toDomain() }
        } else {
            findPublicRooms()
        }
    }

    /**
     * 채팅방이 존재하는지 확인합니다.
     */
    fun existsById(id: Long): Mono<Boolean> {
        return template.exists(
            Query.query(Criteria.where("id").`is`(id)),
            ChatRoomEntity::class.java
        )
    }

    /**
     * 특정 이름의 채팅방이 존재하는지 확인합니다.
     */
    fun existsByName(name: String): Mono<Boolean> {
        return template.exists(
            Query.query(Criteria.where("name").`is`(name)),
            ChatRoomEntity::class.java
        )
    }

    /**
     * 채팅방 수를 조회합니다.
     */
    fun countRooms(): Mono<Long> {
        return template.getDatabaseClient()
            .sql("SELECT COUNT(*) FROM chat_rooms")
            .map { row, _ -> row.get(0, Long::class.java)!! }
            .one()
    }

    /**
     * 특정 사용자가 생성한 채팅방 수를 조회합니다.
     */
    fun countByCreatedBy(userId: Long): Mono<Long> {
        return template.getDatabaseClient()
            .sql("SELECT COUNT(*) FROM chat_rooms WHERE created_by = :userId")
            .bind("userId", userId)
            .map { row, _ -> row.get(0, Long::class.java)!! }
            .one()
    }

    /**
     * 모든 채팅방을 조회합니다.
     */
    fun findAll(): Flux<ChatRoom> {
        return template.select(ChatRoomEntity::class.java)
            .all()
            .map { it.toDomain() }
    }

    /**
     * 채팅방을 삭제합니다.
     */
    fun delete(chatRoom: ChatRoom): Mono<Void> {
        return chatRoom.id?.let { id ->
            template.delete(
                Query.query(Criteria.where("id").`is`(id)),
                ChatRoomEntity::class.java
            ).then()
        } ?: Mono.empty()
    }

    /**
     * ID로 채팅방을 삭제합니다.
     */
    fun deleteById(id: Long): Mono<Void> {
        return template.delete(
            Query.query(Criteria.where("id").`is`(id)),
            ChatRoomEntity::class.java
        ).then()
    }

    /**
     * 특정 사용자가 생성한 모든 채팅방을 삭제합니다.
     */
    fun deleteByCreatedBy(userId: Long): Mono<Void> {
        return template.delete(
            Query.query(Criteria.where("created_by").`is`(userId)),
            ChatRoomEntity::class.java
        ).then()
    }

    /**
     * 모든 채팅방을 삭제합니다.
     */
    fun deleteAll(): Mono<Void> {
        return template.delete(ChatRoomEntity::class.java)
            .all()
            .then()
    }

    /**
     * Row를 ChatRoomEntity로 매핑하는 헬퍼 함수
     */
    private fun mapRowToChatRoomEntity(row: io.r2dbc.spi.Row): ChatRoomEntity {
        return ChatRoomEntity(
            id = row.get("id", Long::class.java),
            name = row.get("name", String::class.java)!!,
            description = row.get("description", String::class.java),
            createdBy = row.get("created_by", Long::class.java)!!,
            isPrivate = row.get("is_private", Boolean::class.java)!!,
            maxParticipants = row.get("max_participants", Integer::class.java)!!.toInt(),
            createdAt = row.get("created_at", LocalDateTime::class.java)!!,
            updatedAt = row.get("updated_at", LocalDateTime::class.java)!!
        )
    }
}