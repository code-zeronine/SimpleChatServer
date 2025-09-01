package com.simplechat.domain.repository

import com.simplechat.domain.entity.User
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * User 도메인을 위한 Repository 인터페이스
 * 
 * 데이터 영속성 계층에 대한 약속(contract)을 정의합니다.
 * 구현체는 Infrastructure 계층에 위치합니다.
 */
interface UserRepository {

    fun findById(id: Long): Mono<User>

    fun findAll(): Flux<User>

    fun save(domain: User): Mono<User>

    fun deleteById(id: Long): Mono<Void>

    fun delete(domain: User): Mono<Void>

    fun deleteAll(): Mono<Void>

    fun existsById(id: Long): Mono<Boolean>

    fun count(): Mono<Long>

    fun findByEmail(email: String): Mono<User>

    fun findByNickname(nickname: String): Mono<User>

    fun existsByEmail(email: String): Mono<Boolean>

    fun existsByNickname(nickname: String): Mono<Boolean>

    fun findByEmailOrNickname(email: String, nickname: String): Mono<User>

    fun findRecentUsers(limit: Int): Flux<User>

    fun findUsersCreatedAfter(afterDate: String): Flux<User>
    
    fun countUsers(): Mono<Long>
}
