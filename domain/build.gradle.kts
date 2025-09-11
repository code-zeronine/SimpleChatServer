plugins {
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    implementation("org.springframework:spring-context")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.data:spring-data-mongodb")
    implementation("io.projectreactor:reactor-core")
    
    
    // Jackson for WebSocket message serialization
    implementation("com.fasterxml.jackson.core:jackson-core")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    
    // Test dependencies
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("io.kotest:kotest-property:5.9.1")
}