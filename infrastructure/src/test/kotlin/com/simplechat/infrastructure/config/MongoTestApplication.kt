package com.simplechat.infrastructure.config

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ComponentScan(basePackages = [
    "com.simplechat.infrastructure.config",
    "com.simplechat.infrastructure.repository"
])
class MongoTestApplication