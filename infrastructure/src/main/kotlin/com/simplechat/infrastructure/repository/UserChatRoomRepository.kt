package com.simplechat.infrastructure.repository

import com.simplechat.domain.entity.ChatRoomRole
import com.simplechat.domain.entity.UserChatRoom
import com.simplechat.infrastructure.entity.UserChatRoomEntity
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.relational.core.query.Criteria
import org.springframework.data.relational.core.query.Query
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.LocalDateTime

/**
 * UserChatRoom 도메인을 위한 Repository 구현체
 * 
 * 사용자와 채팅방 간의 다대다 관계를 관리합니다.
 * R2dbcEntityTemplate을 사용하여 도메인 객체를 반환합니다.
 */
@Repository
class UserChatRoomRepository(
    private val template: R2dbcEntityTemplate
) {

    /**
     * 사용자-채팅방 관계를 저장합니다.
     */
    fun save(userChatRoom: UserChatRoom): Mono<UserChatRoom> {
        val entity = UserChatRoomEntity.fromDomain(userChatRoom)
        return template.insert(entity).map { it.toDomain() }
    }

    /**
     * 여러 사용자-채팅방 관계를 저장합니다.
     */
    fun saveAll(userChatRooms: Iterable<UserChatRoom>): Flux<UserChatRoom> {
        return Flux.fromIterable(userChatRooms)
            .flatMap { save(it) }
    }

    /**
     * 사용자-채팅방 관계를 업데이트합니다.
     */
    fun update(userChatRoom: UserChatRoom): Mono<UserChatRoom> {
        val entity = UserChatRoomEntity.fromDomain(userChatRoom)
        return template.update(entity).map { it.toDomain() }
    }

    /**
     * 사용자 ID와 채팅방 ID로 관계를 조회합니다.
     */
    fun findByUserIdAndChatRoomId(userId: Long, chatRoomId: Long): Mono<UserChatRoom> {
        return template.selectOne(
            Query.query(
                Criteria.where("user_id").`is`(userId)
                    .and("chat_room_id").`is`(chatRoomId)
            ),
            UserChatRoomEntity::class.java
        ).map { it.toDomain() }
    }

    /**
     * 특정 사용자가 참여한 모든 채팅방 관계를 조회합니다.
     */
    fun findByUserId(userId: Long): Flux<UserChatRoom> {
        return template.select(
            Query.query(Criteria.where("user_id").`is`(userId)),
            UserChatRoomEntity::class.java
        ).map { it.toDomain() }
    }

    /**
     * 특정 사용자가 활성 상태로 참여한 채팅방들을 조회합니다.
     */
    fun findActiveRoomsByUserId(userId: Long): Flux<UserChatRoom> {
        return template.select(
            Query.query(
                Criteria.where("user_id").`is`(userId)
                    .and("is_active").`is`(true)
                    .and("left_at").isNull
            ),
            UserChatRoomEntity::class.java
        ).map { it.toDomain() }
    }

    /**
     * 특정 채팅방의 모든 참여자를 조회합니다.
     */
    fun findByChatRoomId(chatRoomId: Long): Flux<UserChatRoom> {
        return template.select(
            Query.query(Criteria.where("chat_room_id").`is`(chatRoomId)),
            UserChatRoomEntity::class.java
        ).map { it.toDomain() }
    }

    /**
     * 특정 채팅방의 활성 참여자들을 조회합니다.
     */
    fun findActiveParticipantsByChatRoomId(chatRoomId: Long): Flux<UserChatRoom> {
        return template.select(
            Query.query(
                Criteria.where("chat_room_id").`is`(chatRoomId)
                    .and("is_active").`is`(true)
                    .and("left_at").isNull
            ),
            UserChatRoomEntity::class.java
        ).map { it.toDomain() }
    }

    /**
     * 특정 채팅방에서 특정 역할을 가진 사용자들을 조회합니다.
     */
    fun findByChatRoomIdAndRole(chatRoomId: Long, role: ChatRoomRole): Flux<UserChatRoom> {
        return template.select(
            Query.query(
                Criteria.where("chat_room_id").`is`(chatRoomId)
                    .and("role").`is`(role.name)
                    .and("is_active").`is`(true)
            ),
            UserChatRoomEntity::class.java
        ).map { it.toDomain() }
    }

    /**
     * 특정 채팅방의 소유자를 조회합니다.
     */
    fun findOwnerByChatRoomId(chatRoomId: Long): Mono<UserChatRoom> {
        return template.selectOne(
            Query.query(
                Criteria.where("chat_room_id").`is`(chatRoomId)
                    .and("role").`is`(ChatRoomRole.OWNER.name)
                    .and("is_active").`is`(true)
            ),
            UserChatRoomEntity::class.java
        ).map { it.toDomain() }
    }

    /**
     * 특정 채팅방의 관리자들(ADMIN 이상)을 조회합니다.
     */
    fun findAdminsByChatRoomId(chatRoomId: Long): Flux<UserChatRoom> {
        return template.getDatabaseClient()
            .sql("SELECT * FROM user_chat_rooms WHERE chat_room_id = :chatRoomId AND role IN ('ADMIN', 'OWNER') AND is_active = TRUE")
            .bind("chatRoomId", chatRoomId)
            .map { row, _ -> mapRowToUserChatRoomEntity(row).toDomain() }
            .all()
    }

    /**
     * 사용자가 고정한 채팅방들을 조회합니다.
     */
    fun findPinnedRoomsByUserId(userId: Long): Flux<UserChatRoom> {
        return template.select(
            Query.query(
                Criteria.where("user_id").`is`(userId)
                    .and("is_pinned").`is`(true)
                    .and("is_active").`is`(true)
            ),
            UserChatRoomEntity::class.java
        ).map { it.toDomain() }
    }

    /**
     * 사용자가 음소거한 채팅방들을 조회합니다.
     */
    fun findMutedRoomsByUserId(userId: Long): Flux<UserChatRoom> {
        return template.select(
            Query.query(
                Criteria.where("user_id").`is`(userId)
                    .and("is_muted").`is`(true)
                    .and("is_active").`is`(true)
            ),
            UserChatRoomEntity::class.java
        ).map { it.toDomain() }
    }

    /**
     * 사용자의 읽지 않은 메시지가 있을 가능성이 있는 채팅방들을 조회합니다.
     */
    fun findUnreadRoomsByUserId(userId: Long): Flux<UserChatRoom> {
        return template.getDatabaseClient()
            .sql("SELECT * FROM user_chat_rooms WHERE user_id = :userId AND is_active = TRUE AND (last_read_at IS NULL OR last_read_at < updated_at)")
            .bind("userId", userId)
            .map { row, _ -> mapRowToUserChatRoomEntity(row).toDomain() }
            .all()
    }

    /**
     * 특정 사용자에 의해 초대된 관계들을 조회합니다.
     */
    fun findByInvitedBy(invitedBy: Long): Flux<UserChatRoom> {
        return template.select(
            Query.query(Criteria.where("invited_by").`is`(invitedBy)),
            UserChatRoomEntity::class.java
        ).map { it.toDomain() }
    }

    /**
     * 최근에 참여한 채팅방들을 조회합니다.
     */
    fun findRecentRoomsByUserId(userId: Long, limit: Int): Flux<UserChatRoom> {
        return template.getDatabaseClient()
            .sql("SELECT * FROM user_chat_rooms WHERE user_id = :userId AND is_active = TRUE ORDER BY joined_at DESC LIMIT :limit")
            .bind("userId", userId)
            .bind("limit", limit)
            .map { row, _ -> mapRowToUserChatRoomEntity(row).toDomain() }
            .all()
    }

    /**
     * 특정 날짜 이후에 참여한 관계들을 조회합니다.
     */
    fun findByJoinedAfter(afterDate: LocalDateTime): Flux<UserChatRoom> {
        return template.select(
            Query.query(Criteria.where("joined_at").greaterThanOrEquals(afterDate)),
            UserChatRoomEntity::class.java
        ).map { it.toDomain() }
    }

    /**
     * 특정 채팅방에서 나간 사용자들을 조회합니다.
     */
    fun findLeftParticipantsByChatRoomId(chatRoomId: Long): Flux<UserChatRoom> {
        return template.select(
            Query.query(
                Criteria.where("chat_room_id").`is`(chatRoomId)
                    .and("left_at").isNotNull
            ),
            UserChatRoomEntity::class.java
        ).map { it.toDomain() }
    }

    /**
     * 사용자-채팅방 관계가 존재하는지 확인합니다.
     */
    fun existsByUserIdAndChatRoomId(userId: Long, chatRoomId: Long): Mono<Boolean> {
        return template.exists(
            Query.query(
                Criteria.where("user_id").`is`(userId)
                    .and("chat_room_id").`is`(chatRoomId)
            ),
            UserChatRoomEntity::class.java
        )
    }

    /**
     * 사용자가 특정 채팅방에 활성 상태로 참여하고 있는지 확인합니다.
     */
    fun isActiveParticipant(userId: Long, chatRoomId: Long): Mono<Boolean> {
        return template.exists(
            Query.query(
                Criteria.where("user_id").`is`(userId)
                    .and("chat_room_id").`is`(chatRoomId)
                    .and("is_active").`is`(true)
                    .and("left_at").isNull
            ),
            UserChatRoomEntity::class.java
        )
    }

    /**
     * 특정 채팅방의 활성 참여자 수를 조회합니다.
     */
    fun countActiveParticipantsByChatRoomId(chatRoomId: Long): Mono<Long> {
        return template.getDatabaseClient()
            .sql("SELECT COUNT(*) FROM user_chat_rooms WHERE chat_room_id = :chatRoomId AND is_active = TRUE AND left_at IS NULL")
            .bind("chatRoomId", chatRoomId)
            .map { row, _ -> row.get(0, Long::class.java)!! }
            .one()
    }

    /**
     * 특정 사용자가 참여한 활성 채팅방 수를 조회합니다.
     */
    fun countActiveRoomsByUserId(userId: Long): Mono<Long> {
        return template.getDatabaseClient()
            .sql("SELECT COUNT(*) FROM user_chat_rooms WHERE user_id = :userId AND is_active = TRUE AND left_at IS NULL")
            .bind("userId", userId)
            .map { row, _ -> row.get(0, Long::class.java)!! }
            .one()
    }

    /**
     * 특정 역할을 가진 사용자 수를 조회합니다.
     */
    fun countByRole(role: ChatRoomRole): Mono<Long> {
        return template.getDatabaseClient()
            .sql("SELECT COUNT(*) FROM user_chat_rooms WHERE role = :role AND is_active = TRUE")
            .bind("role", role.name)
            .map { row, _ -> row.get(0, Long::class.java)!! }
            .one()
    }

    /**
     * 특정 채팅방의 총 참여자 수를 조회합니다 (활성/비활성 모두 포함).
     */
    fun countByChatRoomId(chatRoomId: Long): Mono<Long> {
        return template.getDatabaseClient()
            .sql("SELECT COUNT(*) FROM user_chat_rooms WHERE chat_room_id = :chatRoomId")
            .bind("chatRoomId", chatRoomId)
            .map { row, _ -> row.get(0, Long::class.java)!! }
            .one()
    }

    /**
     * 사용자-채팅방 관계를 삭제합니다 (물리적 삭제).
     */
    fun delete(userChatRoom: UserChatRoom): Mono<Void> {
        return template.delete(
            Query.query(
                Criteria.where("user_id").`is`(userChatRoom.userId)
                    .and("chat_room_id").`is`(userChatRoom.chatRoomId)
            ),
            UserChatRoomEntity::class.java
        ).then()
    }

    /**
     * 사용자 ID와 채팅방 ID로 관계를 삭제합니다 (물리적 삭제).
     */
    fun deleteByUserIdAndChatRoomId(userId: Long, chatRoomId: Long): Mono<Void> {
        return template.delete(
            Query.query(
                Criteria.where("user_id").`is`(userId)
                    .and("chat_room_id").`is`(chatRoomId)
            ),
            UserChatRoomEntity::class.java
        ).then()
    }

    /**
     * 특정 채팅방의 모든 관계를 삭제합니다.
     */
    fun deleteByChatRoomId(chatRoomId: Long): Mono<Void> {
        return template.delete(
            Query.query(Criteria.where("chat_room_id").`is`(chatRoomId)),
            UserChatRoomEntity::class.java
        ).then()
    }

    /**
     * 특정 사용자의 모든 채팅방 관계를 삭제합니다.
     */
    fun deleteByUserId(userId: Long): Mono<Void> {
        return template.delete(
            Query.query(Criteria.where("user_id").`is`(userId)),
            UserChatRoomEntity::class.java
        ).then()
    }

    /**
     * 모든 관계를 삭제합니다.
     */
    fun deleteAll(): Mono<Void> {
        return template.delete(UserChatRoomEntity::class.java)
            .all()
            .then()
    }

    /**
     * Row를 UserChatRoomEntity로 매핑하는 헬퍼 함수
     */
    private fun mapRowToUserChatRoomEntity(row: io.r2dbc.spi.Row): UserChatRoomEntity {
        return UserChatRoomEntity(
            userId = row.get("user_id", Long::class.java)!!,
            chatRoomId = row.get("chat_room_id", Long::class.java)!!,
            role = row.get("role", String::class.java)!!,
            joinedAt = row.get("joined_at", LocalDateTime::class.java)!!,
            isActive = row.get("is_active", Boolean::class.java)!!,
            lastReadAt = row.get("last_read_at", LocalDateTime::class.java),
            isMuted = row.get("is_muted", Boolean::class.java)!!,
            isPinned = row.get("is_pinned", Boolean::class.java)!!,
            leftAt = row.get("left_at", LocalDateTime::class.java),
            invitedBy = row.get("invited_by", Long::class.java),
            updatedAt = row.get("updated_at", LocalDateTime::class.java)!!
        )
    }
}