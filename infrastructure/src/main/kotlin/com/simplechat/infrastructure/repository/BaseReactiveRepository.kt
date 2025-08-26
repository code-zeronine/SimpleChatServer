package com.simplechat.infrastructure.repository

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.relational.core.query.Criteria
import org.springframework.data.relational.core.query.Query
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * 반응형 Repository의 기본 구현을 제공하는 추상 클래스
 * 
 * 공통적인 CRUD 연산과 도메인-엔티티 변환 로직을 캡슐화합니다.
 * 각 Repository는 이 클래스를 상속하여 특화된 로직만 구현하면 됩니다.
 * 
 * @param DOMAIN 도메인 객체 타입
 * @param ENTITY 데이터베이스 엔티티 타입
 * @param ID 식별자 타입
 */
abstract class BaseReactiveRepository<DOMAIN, ENTITY : Any, ID>(
    protected val template: R2dbcEntityTemplate,
    protected val entityClass: Class<ENTITY>
) {

    /**
     * 도메인 객체를 엔티티로 변환합니다.
     */
    abstract fun fromDomain(domain: DOMAIN): ENTITY

    /**
     * 엔티티를 도메인 객체로 변환합니다.
     */
    abstract fun toDomain(entity: ENTITY): DOMAIN

    /**
     * 도메인 객체에서 ID를 추출합니다.
     */
    abstract fun extractId(domain: DOMAIN): ID?

    /**
     * ID 컬럼명을 반환합니다. (기본값: "id")
     */
    protected open fun getIdColumnName(): String = "id"

    /**
     * 도메인 객체를 저장합니다. (신규 생성 또는 업데이트)
     */
    open fun save(domain: DOMAIN): Mono<DOMAIN> {
        val entity = fromDomain(domain)
        return if (extractId(domain) == null) {
            // 신규 생성
            template.insert(entity).map { toDomain(it) }
        } else {
            // 업데이트
            template.update(entity).map { toDomain(it) }
        }
    }

    /**
     * 여러 도메인 객체를 저장합니다.
     */
    open fun saveAll(domains: Iterable<DOMAIN>): Flux<DOMAIN> {
        return Flux.fromIterable(domains)
            .flatMap { save(it) }
    }

    /**
     * ID로 도메인 객체를 조회합니다.
     */
    open fun findById(id: ID): Mono<DOMAIN> {
        return template.selectOne(
            Query.query(Criteria.where(getIdColumnName()).`is`(id as Any)),
            entityClass
        ).map { toDomain(it) }
    }

    /**
     * 모든 도메인 객체를 조회합니다.
     */
    open fun findAll(): Flux<DOMAIN> {
        return template.select(entityClass)
            .all()
            .map { toDomain(it) }
    }

    /**
     * ID가 존재하는지 확인합니다.
     */
    open fun existsById(id: ID): Mono<Boolean> {
        return template.exists(
            Query.query(Criteria.where(getIdColumnName()).`is`(id as Any)),
            entityClass
        )
    }

    /**
     * 전체 개수를 조회합니다.
     */
    open fun count(): Mono<Long> {
        return template.count(Query.empty(), entityClass)
    }

    /**
     * 도메인 객체를 삭제합니다.
     */
    open fun delete(domain: DOMAIN): Mono<Void> {
        return extractId(domain)?.let { id ->
            deleteById(id)
        } ?: Mono.empty()
    }

    /**
     * ID로 객체를 삭제합니다.
     */
    open fun deleteById(id: ID): Mono<Void> {
        return template.delete(
            Query.query(Criteria.where(getIdColumnName()).`is`(id as Any)),
            entityClass
        ).then()
    }

    /**
     * 모든 객체를 삭제합니다.
     */
    open fun deleteAll(): Mono<Void> {
        return template.delete(entityClass)
            .all()
            .then()
    }

    /**
     * 조건에 맞는 객체들을 조회합니다.
     */
    protected fun findByCriteria(criteria: Criteria): Flux<DOMAIN> {
        return template.select(
            Query.query(criteria),
            entityClass
        ).map { toDomain(it) }
    }

    /**
     * 조건에 맞는 단일 객체를 조회합니다.
     */
    protected fun findOneByCriteria(criteria: Criteria): Mono<DOMAIN> {
        return template.selectOne(
            Query.query(criteria),
            entityClass
        ).map { toDomain(it) }
    }

    /**
     * 조건에 맞는 객체의 개수를 조회합니다.
     */
    protected fun countByCriteria(criteria: Criteria): Mono<Long> {
        return template.count(
            Query.query(criteria),
            entityClass
        )
    }

    /**
     * 조건에 맞는 객체가 존재하는지 확인합니다.
     */
    protected fun existsByCriteria(criteria: Criteria): Mono<Boolean> {
        return template.exists(
            Query.query(criteria),
            entityClass
        )
    }

    /**
     * 조건에 맞는 객체들을 삭제합니다.
     */
    protected fun deleteByCriteria(criteria: Criteria): Mono<Void> {
        return template.delete(
            Query.query(criteria),
            entityClass
        ).then()
    }
}