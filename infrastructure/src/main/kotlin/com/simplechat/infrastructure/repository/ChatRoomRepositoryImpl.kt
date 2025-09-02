package com.simplechat.infrastructure.repository

import com.simplechat.domain.entity.ChatRoom
import com.simplechat.domain.repository.ChatRoomRepository
import com.simplechat.infrastructure.entity.ChatRoomEntity
import com.simplechat.infrastructure.util.RowMapper.getBoolean
import com.simplechat.infrastructure.util.RowMapper.getInt
import com.simplechat.infrastructure.util.RowMapper.getLocalDateTime
import com.simplechat.infrastructure.util.RowMapper.getLong
import com.simplechat.infrastructure.util.RowMapper.getLongOrNull
import com.simplechat.infrastructure.util.RowMapper.getString
import com.simplechat.infrastructure.util.RowMapper.getStringOrNull
import com.simplechat.infrastructure.util.RowMapper.mapRowSafely
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.relational.core.query.Criteria
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDateTime

/**
 * ChatRoom 도메인을 위한 Repository 구현체
 * 
 * BaseReactiveRepository를 상속하여 공통 로직을 재사용하고
 * ChatRoom 특화 로직만 구현합니다.
 */
@Repository
class ChatRoomRepositoryImpl(
    template: R2dbcEntityTemplate
) : BaseReactiveRepository<ChatRoom, ChatRoomEntity, Long>(template, ChatRoomEntity::class.java), ChatRoomRepository {

    override fun fromDomain(domain: ChatRoom): ChatRoomEntity {
        return ChatRoomEntity.fromDomain(domain)
    }

    override fun toDomain(entity: ChatRoomEntity): ChatRoom {
        return entity.toDomain()
    }

    override fun extractId(domain: ChatRoom): Long? {
        return domain.id
    }

    // 공통 메서드들은 BaseReactiveRepository에서 상속됨

    /**
     * 채팅방 이름으로 조회합니다.
     */
    override fun findByName(name: String): Mono<ChatRoom> {
        return findOneByCriteria(Criteria.where("name").`is`(name))
    }

    /**
     * 특정 사용자가 생성한 채팅방들을 조회합니다.
     */
    override fun findByCreatedBy(userId: Long): Flux<ChatRoom> {
        return findByCriteria(Criteria.where("created_by").`is`(userId))
    }

    /**
     * 공개 채팅방들을 조회합니다.
     */
    override fun findPublicRooms(): Flux<ChatRoom> {
        return findByCriteria(Criteria.where("is_private").`is`(false))
    }

    /**
     * 비공개 채팅방들을 조회합니다.
     */
    override fun findPrivateRooms(): Flux<ChatRoom> {
        return findByCriteria(Criteria.where("is_private").`is`(true))
    }

    /**
     * 채팅방 이름으로 검색합니다. (부분 일치)
     */
    override fun findByNameContaining(namePattern: String): Flux<ChatRoom> {
        return template.getDatabaseClient()
            .sql("SELECT * FROM chat_rooms WHERE name ILIKE :pattern ORDER BY created_at DESC")
            .bind("pattern", "%$namePattern%")
            .map { row, _ -> mapRowToChatRoomEntity(row).toDomain() }
            .all()
    }

    /**
     * 최근 생성된 채팅방들을 조회합니다.
     */
    override fun findRecentRooms(limit: Int): Flux<ChatRoom> {
        return template.getDatabaseClient()
            .sql("SELECT * FROM chat_rooms ORDER BY created_at DESC LIMIT :limit")
            .bind("limit", limit)
            .map { row, _ -> mapRowToChatRoomEntity(row).toDomain() }
            .all()
    }

    /**
     * 특정 일자 이후에 생성된 채팅방들을 조회합니다.
     */
    override fun findRoomsCreatedAfter(afterDate: LocalDateTime): Flux<ChatRoom> {
        return template.getDatabaseClient()
            .sql("SELECT * FROM chat_rooms WHERE created_at >= :afterDate ORDER BY created_at")
            .bind("afterDate", afterDate)
            .map { row, _ -> mapRowToChatRoomEntity(row).toDomain() }
            .all()
    }

    /**
     * 활성 채팅방들을 조회합니다. (최근 24시간 내 업데이트된 방)
     */
    override fun findActiveRooms(): Flux<ChatRoom> {
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
    override fun findAvailablePublicRooms(excludeCreatedBy: Long?): Flux<ChatRoom> {
        return if (excludeCreatedBy != null) {
            findByCriteria(
                Criteria.where("is_private").`is`(false)
                    .and("created_by").not(excludeCreatedBy)
            )
        } else {
            findPublicRooms()
        }
    }

    /**
     * 특정 이름의 채팅방이 존재하는지 확인합니다.
     */
    override fun existsByName(name: String): Mono<Boolean> {
        return existsByCriteria(Criteria.where("name").`is`(name))
    }

    /**
     * 채팅방 수를 조회합니다.
     */
    override fun countRooms(): Mono<Long> {
        return count() // BaseReactiveRepository의 메서드 사용
    }

    /**
     * 특정 사용자가 생성한 채팅방 수를 조회합니다.
     */
    override fun countByCreatedBy(userId: Long): Mono<Long> {
        return countByCriteria(Criteria.where("created_by").`is`(userId))
    }

    /**
     * 특정 사용자가 생성한 모든 채팅방을 삭제합니다.
     */
    override fun deleteByCreatedBy(userId: Long): Mono<Void> {
        return deleteByCriteria(Criteria.where("created_by").`is`(userId))
    }

    // findAll, delete, deleteById, deleteAll, existsById는 BaseReactiveRepository에서 상속됨

    /**
     * Row를 ChatRoomEntity로 매핑하는 헬퍼 함수 (타입 안전성 개선)
     */
    private fun mapRowToChatRoomEntity(row: io.r2dbc.spi.Row): ChatRoomEntity {
        return mapRowSafely(row) { r ->
            ChatRoomEntity(
                id = r.getLongOrNull("id"),
                name = r.getString("name"),
                description = r.getStringOrNull("description"),
                createdBy = r.getLong("created_by"),
                isPrivate = r.getBoolean("is_private"),
                maxParticipants = r.getInt("max_participants"),
                createdAt = r.getLocalDateTime("created_at"),
                updatedAt = r.getLocalDateTime("updated_at")
            )
        }
    }
}