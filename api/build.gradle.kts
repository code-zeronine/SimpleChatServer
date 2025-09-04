plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":infrastructure"))
    
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework:spring-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")
    
    // JWT Library
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    
    // API Documentation
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.3.0")

    // MacOS DNS 네이티브 라이브러리 (MacOS에서 DNS 해결 최적화)
    implementation("io.netty:netty-resolver-dns-native-macos:4.1.123.Final:osx-aarch_64")
    implementation("io.netty:netty-resolver-dns-native-macos:4.1.123.Final:osx-x86_64")
    
    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:mongodb:1.19.3")
    testImplementation("org.testcontainers:postgresql:1.19.3")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    testImplementation("com.redis:testcontainers-redis:2.2.2")
}