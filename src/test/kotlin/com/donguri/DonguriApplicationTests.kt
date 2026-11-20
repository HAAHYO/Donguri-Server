package com.donguri

import com.donguri.employee.EmployeeRepository
import com.donguri.global.init.DataInitializer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// 프로필을 지정하지 않은 기동 = 이미지(bootJar)로 띄운 경우. DB 만 H2 로 바꾼다
@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:default_profile;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.jpa.hibernate.ddl-auto=create-drop",
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
}
