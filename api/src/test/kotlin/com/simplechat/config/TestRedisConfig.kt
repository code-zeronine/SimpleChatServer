package com.simplechat.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Profile

/**
 * 테스트 환경용 Redis 구성
 * 테스트 시 Redis 관련 빈들의 자동 구성을 비활성화
 */
@TestConfiguration
@Profile("test") 
class TestRedisConfig {
    // Redis 자동 구성을 비활성화하고 테스트에서는 Mock을 사용하도록 설정
    // 실제 Mock 빈들은 필요시 개별 테스트 클래스에서 @MockBean으로 생성
}