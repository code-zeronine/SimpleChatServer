package com.simplechat.infrastructure.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * 스케줄링 설정
 * 
 * WebSocket 세션 정리와 같은 백그라운드 작업을 위한 스케줄링을 활성화합니다.
 */
@Configuration
@EnableScheduling
class SchedulingConfig