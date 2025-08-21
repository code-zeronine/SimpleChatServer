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
    implementation(project(":domain"))
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    
    // R2DBC PostgreSQL
    implementation("org.postgresql:r2dbc-postgresql")
    implementation("io.r2dbc:r2dbc-pool")
    runtimeOnly("org.postgresql:postgresql")
}