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
}