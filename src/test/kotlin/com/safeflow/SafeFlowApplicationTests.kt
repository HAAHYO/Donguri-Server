package com.safeflow

import com.safeflow.domain.employee.repository.EmployeeRepository
import com.safeflow.global.init.DataInitializer
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.ApplicationContext
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@AutoConfigureMockMvc
@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:default_profile;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "fds.jwt.secret=dGVzdC1vbmx5LWp3dC1zZWNyZXQta2V5LWZvci1kb25ndXJpLWZkcy0zMmJ5dGVz",
    ],
)
class SafeFlowApplicationTests(
    @Autowired private val context: ApplicationContext,
    @Autowired private val employeeRepository: EmployeeRepository,
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `프로필 없이 기동하면 초기 데이터를 시딩하지 않는다`() {
        assertTrue(context.environment.activeProfiles.isEmpty())
        assertTrue(context.getBeanNamesForType(DataInitializer::class.java).isEmpty())
        assertEquals(0, employeeRepository.count())
    }

    @Test
    fun `프로필 없이 기동하면 Flyway 로 스키마를 만들고 SQL 로그를 끈다`() {
        assertEquals(
            "1",
            context
                .getBean(Flyway::class.java)
                .info()
                .current()
                .version.version,
        )
        assertEquals("validate", context.environment.getProperty("spring.jpa.hibernate.ddl-auto"))
        assertFalse(context.environment.getProperty("spring.jpa.show-sql", Boolean::class.java, false))
        assertNull(context.environment.getProperty("spring.jpa.properties.hibernate.format_sql"))
    }

    @Test
    fun `프로필 없이 기동하면 API 문서와 Swagger UI 를 노출하지 않는다`() {
        listOf("/v3/api-docs", "/swagger-ui/index.html", "/swagger-ui.html").forEach { path ->
            mockMvc.get(path).andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("NOT_FOUND") }
            }
        }
    }

    @Test
    fun `Boot 기본 인메모리 사용자를 만들지 않는다`() {
        assertTrue(context.getBeanNamesForType(UserDetailsService::class.java).isEmpty())
    }
}
