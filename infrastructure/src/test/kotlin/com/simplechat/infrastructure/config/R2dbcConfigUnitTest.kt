package com.simplechat.infrastructure.config

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * R2dbcConfig 단위 테스트
 * Spring Context 로딩 없이 설정 클래스 자체의 구조를 검증
 */
class R2dbcConfigUnitTest {

    @Test
    fun `should have proper R2DBC configuration class structure`() {
        // R2dbcConfig 클래스가 존재하고 올바른 어노테이션을 가지는지 확인
        val configClass = R2dbcConfig::class.java
        
        assertNotNull(configClass, "R2dbcConfig class should exist")
        
        // @Configuration 어노테이션 확인
        val configurationAnnotation = configClass.getAnnotation(org.springframework.context.annotation.Configuration::class.java)
        assertNotNull(configurationAnnotation, "R2dbcConfig should have @Configuration annotation")
        
        // @EnableR2dbcRepositories 어노테이션 확인
        val repositoryAnnotation = configClass.getAnnotation(org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories::class.java)
        assertNotNull(repositoryAnnotation, "R2dbcConfig should have @EnableR2dbcRepositories annotation")
        
        // Repository 스캔 패키지 확인
        val basePackages = repositoryAnnotation.basePackages
        assertTrue(basePackages.contains("com.simplechat.infrastructure.repository"), "Should scan infrastructure repository package")
    }

    @Test
    fun `should have all required bean methods`() {
        val configClass = R2dbcConfig::class.java
        val methods = configClass.declaredMethods
        val methodNames = methods.map { it.name }.toSet()
        
        // 필수 빈 메서드들이 존재하는지 확인
        assertTrue(methodNames.contains("connectionFactory"), "Should have connectionFactory method")
        assertTrue(methodNames.contains("reactiveTransactionManager"), "Should have reactiveTransactionManager method")
        assertTrue(methodNames.contains("transactionalOperator"), "Should have transactionalOperator method")
        assertTrue(methodNames.contains("r2dbcEntityTemplate"), "Should have r2dbcEntityTemplate method")
        assertTrue(methodNames.contains("connectionFactoryInitializer"), "Should have connectionFactoryInitializer method")
    }

    @Test
    fun `should extend AbstractR2dbcConfiguration`() {
        val configClass = R2dbcConfig::class.java
        val superClass = configClass.superclass
        
        assertNotNull(superClass, "R2dbcConfig should have a superclass")
        assertTrue(superClass.name.contains("AbstractR2dbcConfiguration"), "Should extend AbstractR2dbcConfiguration")
    }

    @Test
    fun `should have proper field annotations for configuration properties`() {
        val configClass = R2dbcConfig::class.java
        val fields = configClass.declaredFields
        
        val urlField = fields.find { it.name == "url" }
        val usernameField = fields.find { it.name == "username" }
        val passwordField = fields.find { it.name == "password" }
        
        assertNotNull(urlField, "Should have url field")
        assertNotNull(usernameField, "Should have username field")
        assertNotNull(passwordField, "Should have password field")
        
        // @Value 어노테이션 확인
        val urlValueAnnotation = urlField?.getAnnotation(org.springframework.beans.factory.annotation.Value::class.java)
        val usernameValueAnnotation = usernameField?.getAnnotation(org.springframework.beans.factory.annotation.Value::class.java)
        val passwordValueAnnotation = passwordField?.getAnnotation(org.springframework.beans.factory.annotation.Value::class.java)
        
        assertNotNull(urlValueAnnotation, "url field should have @Value annotation")
        assertNotNull(usernameValueAnnotation, "username field should have @Value annotation")
        assertNotNull(passwordValueAnnotation, "password field should have @Value annotation")
        
        // @Value 값 확인
        assertTrue(urlValueAnnotation?.value?.contains("spring.r2dbc.url") == true, "url should be bound to spring.r2dbc.url property")
        assertTrue(usernameValueAnnotation?.value?.contains("spring.r2dbc.username") == true, "username should be bound to spring.r2dbc.username property")
        assertTrue(passwordValueAnnotation?.value?.contains("spring.r2dbc.password") == true, "password should be bound to spring.r2dbc.password property")
    }

    @Test
    fun `bean methods should have proper annotations`() {
        val configClass = R2dbcConfig::class.java
        val methods = configClass.declaredMethods
        
        val beanMethods = methods.filter { method ->
            method.getAnnotation(org.springframework.context.annotation.Bean::class.java) != null
        }
        
        assertTrue(beanMethods.isNotEmpty(), "Should have methods annotated with @Bean")
        
        // 각 빈 메서드의 어노테이션 확인
        beanMethods.forEach { method ->
            val beanAnnotation = method.getAnnotation(org.springframework.context.annotation.Bean::class.java)
            assertNotNull(beanAnnotation, "${method.name} should have @Bean annotation")
        }
    }
}