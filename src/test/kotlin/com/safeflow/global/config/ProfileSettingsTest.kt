package com.safeflow.global.config

import com.safeflow.domain.employee.repository.EmployeeRepository
import com.safeflow.global.init.AdminBootstrap
import com.safeflow.global.init.DataInitializer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:prod_local_profile;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "fds.jwt.secret=dGVzdC1vbmx5LWp3dC1zZWNyZXQta2V5LWZvci1kb25ndXJpLWZkcy0zMmJ5dGVz",
    ],
)
@AutoConfigureMockMvc
@ActiveProfiles("prod", "local")
class ProdWithLocalProfileTest(
    @Autowired private val context: ApplicationContext,
    @Autowired private val employeeRepository: EmployeeRepository,
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `prod 와 local 이 함께 켜져도 시딩과 SQL 로그, Swagger 는 꺼진다`() {
        assertTrue(context.getBeanNamesForType(DataInitializer::class.java).isEmpty())
        assertEquals(0, employeeRepository.count())
        assertFalse(context.environment.getProperty("spring.jpa.show-sql", Boolean::class.java, false))
        listOf("/v3/api-docs", "/swagger-ui/index.html").forEach { path ->
            mockMvc.get(path).andExpect { status { isNotFound() } }
        }
    }
}

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:local_profile;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
    ],
)
@AutoConfigureMockMvc
@ActiveProfiles("local")
class LocalProfileTest(
    @Autowired private val context: ApplicationContext,
    @Autowired private val employeeRepository: EmployeeRepository,
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `local 프로필은 시딩과 SQL 로그, Swagger 를 켜고 운영용 첫 관리자 생성은 끈다`() {
        assertTrue(context.getBeanNamesForType(DataInitializer::class.java).isNotEmpty())
        assertTrue(context.getBeanNamesForType(AdminBootstrap::class.java).isEmpty())
        assertEquals(2, employeeRepository.count())
        assertTrue(context.environment.getProperty("spring.jpa.show-sql", Boolean::class.java, false))
        mockMvc.get("/v3/api-docs").andExpect { status { isOk() } }
    }
}
