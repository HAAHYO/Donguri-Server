package com.donguri

import com.donguri.employee.EmployeeRepository
import com.donguri.global.init.DataInitializer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.security.core.userdetails.UserDetailsService
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:default_profile;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "fds.jwt.secret=dGVzdC1vbmx5LWp3dC1zZWNyZXQta2V5LWZvci1kb25ndXJpLWZkcy0zMmJ5dGVz",
    ],
)
class DonguriApplicationTests(
    @Autowired private val context: ApplicationContext,
    @Autowired private val employeeRepository: EmployeeRepository,
) {
    @Test
    fun `프로필 없이 기동하면 초기 데이터를 시딩하지 않는다`() {
        assertTrue(context.environment.activeProfiles.isEmpty())
        assertTrue(context.getBeanNamesForType(DataInitializer::class.java).isEmpty())
        assertEquals(0, employeeRepository.count())
    }

    @Test
    fun `Boot 기본 인메모리 사용자를 만들지 않는다`() {
        assertTrue(context.getBeanNamesForType(UserDetailsService::class.java).isEmpty())
    }
}
