package com.simplechat.infrastructure.repository

import com.simplechat.domain.entity.User
import com.simplechat.infrastructure.entity.UserEntity
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.relational.core.query.Criteria
import org.springframework.data.relational.core.query.Query
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * User 도메인을 위한 Repository 구현체
 * 
 * R2dbcEntityTemplate을 직접 사용하여 도메인 객체를 반환합니다.
 * UserEntity는 내부 구현 디테일로만 사용됩니다.
 */
@Repository
class UserRepository(
    private val template: R2dbcEntityTemplate
) {

    /**
     * 사용자를 저장합니다. (신규 생성 또는 업데이트)
     */
    fun save(user: User): Mono<User> {
        val userEntity = UserEntity.fromDomain(user)
        return if (user.id == null) {
            // 신규 사용자 생성
            template.insert(userEntity).map { it.toDomain() }
        } else {
            // 기존 사용자 업데이트
            template.update(userEntity).map { it.toDomain() }
        }
    }

    /**
     * 여러 사용자를 저장합니다.
     */
    fun saveAll(users: Iterable<User>): Flux<User> {
        return Flux.fromIterable(users)
            .flatMap { save(it) }
    }

    /**
     * ID로 사용자를 조회합니다.
     */
    fun findById(id: Long): Mono<User> {
        return template.selectOne(
            Query.query(Criteria.where("id").`is`(id)),
            UserEntity::class.java
        ).map { it.toDomain() }
    }

    /**
     * 이메일 주소로 사용자를 조회합니다.
     */
    fun findByEmail(email: String): Mono<User> {
        return template.selectOne(
            Query.query(Criteria.where("email").`is`(email)),
            UserEntity::class.java
        ).map { it.toDomain() }
    }

    /**
     * 닉네임으로 사용자를 조회합니다.
     */
    fun findByNickname(nickname: String): Mono<User> {
        return template.selectOne(
            Query.query(Criteria.where("nickname").`is`(nickname)),
            UserEntity::class.java
        ).map { it.toDomain() }
    }

    /**
     * 해당 이메일 주소를 가진 사용자가 존재하는지 확인합니다.
     */
    fun existsByEmail(email: String): Mono<Boolean> {
        return template.exists(
            Query.query(Criteria.where("email").`is`(email)),
            UserEntity::class.java
        )
    }

    /**
     * 해당 닉네임을 가진 사용자가 존재하는지 확인합니다.
     */
    fun existsByNickname(nickname: String): Mono<Boolean> {
        return template.exists(
            Query.query(Criteria.where("nickname").`is`(nickname)),
            UserEntity::class.java
        )
    }

    /**
     * Row를 UserEntity로 매핑하는 헬퍼 함수
     */
    private fun mapRowToUserEntity(row: io.r2dbc.spi.Row): UserEntity {
        return UserEntity(
            id = row.get("id", Long::class.java),
            email = row.get("email", String::class.java)!!,
            passwordHash = row.get("password_hash", String::class.java)!!,
            nickname = row.get("nickname", String::class.java)!!,
            createdAt = row.get("created_at", java.time.LocalDateTime::class.java)!!
        )
    }

    /**
     * 이메일 또는 닉네임으로 사용자를 조회합니다.
     */
    fun findByEmailOrNickname(email: String, nickname: String): Mono<User> {
        return template.getDatabaseClient()
            .sql("SELECT * FROM users WHERE email = :email OR nickname = :nickname")
            .bind("email", email)
            .bind("nickname", nickname)
            .map { row, _ -> mapRowToUserEntity(row).toDomain() }
            .one()
    }

    /**
     * 최근 생성된 사용자들을 조회합니다.
     */
    fun findRecentUsers(limit: Int): Flux<User> {
        return template.getDatabaseClient()
            .sql("SELECT * FROM users ORDER BY created_at DESC LIMIT :limit")
            .bind("limit", limit)
            .map { row, _ -> mapRowToUserEntity(row).toDomain() }
            .all()
    }

    /**
     * 특정 일자 이후에 생성된 사용자들을 조회합니다.
     */
    fun findUsersCreatedAfter(afterDate: String): Flux<User> {
        return template.getDatabaseClient()
            .sql("SELECT * FROM users WHERE created_at >= TO_TIMESTAMP(:afterDate, 'YYYY-MM-DD') ORDER BY created_at")
            .bind("afterDate", afterDate)
            .map { row, _ -> mapRowToUserEntity(row).toDomain() }
            .all()
    }

    /**
     * 사용자 수를 조회합니다.
     */
    fun countUsers(): Mono<Long> {
        return template.getDatabaseClient()
            .sql("SELECT COUNT(*) FROM users")
            .map { row, _ -> row.get(0, Long::class.java)!! }
            .one()
    }

    /**
     * 모든 사용자를 조회합니다.
     */
    fun findAll(): Flux<User> {
        return template.select(UserEntity::class.java)
            .all()
            .map { it.toDomain() }
    }

    /**
     * 사용자를 삭제합니다.
     */
    fun delete(user: User): Mono<Void> {
        return user.id?.let { id ->
            template.delete(
                Query.query(Criteria.where("id").`is`(id)),
                UserEntity::class.java
            ).then()
        } ?: Mono.empty()
    }

    /**
     * ID로 사용자를 삭제합니다.
     */
    fun deleteById(id: Long): Mono<Void> {
        return template.delete(
            Query.query(Criteria.where("id").`is`(id)),
            UserEntity::class.java
        ).then()
    }

    /**
     * 모든 사용자를 삭제합니다.
     */
    fun deleteAll(): Mono<Void> {
        return template.delete(UserEntity::class.java)
            .all()
            .then()
    }

    /**
     * 사용자가 존재하는지 확인합니다.
     */
    fun existsById(id: Long): Mono<Boolean> {
        return template.exists(
            Query.query(Criteria.where("id").`is`(id)),
            UserEntity::class.java
        )
    }
}