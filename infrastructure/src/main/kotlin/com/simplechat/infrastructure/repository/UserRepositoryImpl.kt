package com.simplechat.infrastructure.repository

import com.simplechat.domain.entity.User
import com.simplechat.domain.repository.UserRepository
import com.simplechat.infrastructure.entity.UserEntity
import com.simplechat.infrastructure.util.RowMapper.getLocalDateTime
import com.simplechat.infrastructure.util.RowMapper.getLongOrNull
import com.simplechat.infrastructure.util.RowMapper.getString
import com.simplechat.infrastructure.util.RowMapper.mapRowSafely
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.relational.core.query.Criteria
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * User 도메인을 위한 Repository 구현체
 * 
 * BaseReactiveRepository를 상속하여 공통 로직을 재사용하고
 * User 특화 로직만 구현합니다.
 */
@Repository
class UserRepositoryImpl(
    template: R2dbcEntityTemplate
) : BaseReactiveRepository<User, UserEntity, Long>(template, UserEntity::class.java), UserRepository {

    override fun fromDomain(domain: User): UserEntity {
        return UserEntity.fromDomain(domain)
    }

    override fun toDomain(entity: UserEntity): User {
        return entity.toDomain()
    }

    override fun extractId(domain: User): Long? {
        return domain.id
    }

    // 공통 메서드들은 BaseReactiveRepository에서 상속됨

    /**
     * 이메일 주소로 사용자를 조회합니다.
     */
    override fun findByEmail(email: String): Mono<User> {
        return findOneByCriteria(Criteria.where("email").`is`(email))
    }

    /**
     * 닉네임으로 사용자를 조회합니다.
     */
    override fun findByNickname(nickname: String): Mono<User> {
        return findOneByCriteria(Criteria.where("nickname").`is`(nickname))
    }

    /**
     * 해당 이메일 주소를 가진 사용자가 존재하는지 확인합니다.
     */
    override fun existsByEmail(email: String): Mono<Boolean> {
        return existsByCriteria(Criteria.where("email").`is`(email))
    }

    /**
     * 해당 닉네임을 가진 사용자가 존재하는지 확인합니다.
     */
    override fun existsByNickname(nickname: String): Mono<Boolean> {
        return existsByCriteria(Criteria.where("nickname").`is`(nickname))
    }

    /**
     * Row를 UserEntity로 매핑하는 헬퍼 함수 (타입 안전성 개선)
     */
    private fun mapRowToUserEntity(row: io.r2dbc.spi.Row): UserEntity {
        return mapRowSafely(row) { r ->
            UserEntity(
                id = r.getLongOrNull("id"),
                email = r.getString("email"),
                passwordHash = r.getString("password_hash"),
                nickname = r.getString("nickname"),
                createdAt = r.getLocalDateTime("created_at")
            )
        }
    }

    /**
     * 이메일 또는 닉네임으로 사용자를 조회합니다.
     */
    override fun findByEmailOrNickname(email: String, nickname: String): Mono<User> {
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
    override fun findRecentUsers(limit: Int): Flux<User> {
        return template.getDatabaseClient()
            .sql("SELECT * FROM users ORDER BY created_at DESC LIMIT :limit")
            .bind("limit", limit)
            .map { row, _ -> mapRowToUserEntity(row).toDomain() }
            .all()
    }

    /**
     * 특정 일자 이후에 생성된 사용자들을 조회합니다.
     */
    override fun findUsersCreatedAfter(afterDate: String): Flux<User> {
        return template.getDatabaseClient()
            .sql("SELECT * FROM users WHERE created_at >= TO_TIMESTAMP(:afterDate, 'YYYY-MM-DD') ORDER BY created_at")
            .bind("afterDate", afterDate)
            .map { row, _ -> mapRowToUserEntity(row).toDomain() }
            .all()
    }

    /**
     * 사용자 수를 조회합니다.
     */
    override fun countUsers(): Mono<Long> {
        return count() // BaseReactiveRepository의 메서드 사용
    }

    // findAll, delete, deleteById, deleteAll, existsById는 BaseReactiveRepository에서 상속됨
}