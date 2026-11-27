package com.safeflow.global.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalCorsDefaultsTest {
    private val runner =
        ApplicationContextRunner()
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withUserConfiguration(CorsPropertiesConfig::class.java)

    @Test
    fun `local 프로필은 CORS_ALLOWED_ORIGINS 가 없으면 3000 과 5173 을 허용한다`() {
        runner.withPropertyValues("spring.profiles.active=local").run { context ->
            assertEquals(
                listOf("http://localhost:3000", "http://localhost:5173"),
                context.getBean(CorsProperties::class.java).allowedOrigins,
            )
        }
    }

    @Test
    fun `프로필이 없으면 허용 Origin 이 없다`() {
        runner.run { context ->
            assertEquals(emptyList(), context.getBean(CorsProperties::class.java).allowedOrigins.filter { it.isNotBlank() })
        }
    }

    @Configuration
    @EnableConfigurationProperties(CorsProperties::class)
    class CorsPropertiesConfig
}
