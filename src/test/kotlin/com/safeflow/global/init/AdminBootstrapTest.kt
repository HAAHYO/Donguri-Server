package com.safeflow.global.init

import com.safeflow.domain.employee.entity.EmployeeRole
import com.safeflow.domain.employee.repository.EmployeeRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AdminBootstrapTest(
    @Autowired private val employeeRepository: EmployeeRepository,
) {
    private val passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    @Test
    fun `직원이 없으면 설정한 아이디로 FDS_ADMIN 계정 하나를 bcrypt 비밀번호로 만든다`() {
        bootstrap("admin", PASSWORD).run(DefaultApplicationArguments())

        val employees = employeeRepository.findAll()
        assertEquals(1, employees.size)
        val admin = employees.single()
        assertEquals("admin", admin.username)
        assertEquals(EmployeeRole.FDS_ADMIN, admin.role)
        assertTrue(admin.password.startsWith("{bcrypt}"))
        assertTrue(passwordEncoder.matches(PASSWORD, admin.password))
    }

    private fun bootstrap(
        username: String,
        password: String,
    ) = AdminBootstrap(employeeRepository, passwordEncoder, BootstrapAdminProperties(username, password))

    companion object {
        private const val PASSWORD = "first-admin-pass-123"
    }
}
