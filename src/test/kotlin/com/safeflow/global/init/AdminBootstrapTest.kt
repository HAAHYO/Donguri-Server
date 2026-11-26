package com.safeflow.global.init

import com.safeflow.domain.employee.entity.Employee
import com.safeflow.domain.employee.entity.EmployeeRole
import com.safeflow.domain.employee.repository.EmployeeRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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

    @Test
    fun `이미 직원이 있으면 계정을 만들거나 비밀번호를 바꾸지 않는다`() {
        val existing = employeeRepository.save(Employee("fds_admin", "{bcrypt}original", EmployeeRole.FDS_ADMIN, "이상거래탐지팀"))

        bootstrap("fds_admin", PASSWORD).run(DefaultApplicationArguments())
        bootstrap("another", PASSWORD).run(DefaultApplicationArguments())

        assertEquals(listOf(existing.id), employeeRepository.findAll().map { it.id })
        assertEquals("{bcrypt}original", employeeRepository.findAll().single().password)
    }

    @Test
    fun `아이디와 비밀번호가 모두 비어 있으면 아무 계정도 만들지 않는다`() {
        bootstrap("", "").run(DefaultApplicationArguments())
        bootstrap("  ", "   ").run(DefaultApplicationArguments())

        assertEquals(0, employeeRepository.count())
    }

    @Test
    fun `직원이 없을 때 아이디와 비밀번호 중 하나만 설정하면 계정을 만들지 않고 실패한다`() {
        listOf("" to PASSWORD, "admin" to "", "admin" to "   ").forEach { (username, password) ->
            val error = assertThrows<IllegalStateException> { bootstrap(username, password).run(DefaultApplicationArguments()) }
            assertTrue(error.message!!.contains("FDS_ADMIN_USERNAME"))
        }

        assertEquals(0, employeeRepository.count())
    }

    @Test
    fun `계정을 만든 뒤 비밀번호만 지운 설정으로 재기동해도 실패하지 않는다`() {
        bootstrap("admin", PASSWORD).run(DefaultApplicationArguments())

        bootstrap("admin", "").run(DefaultApplicationArguments())

        assertEquals(1, employeeRepository.count())
    }

    @Test
    fun `비밀번호가 12자 미만이면 계정을 만들지 않고 실패한다`() {
        assertThrows<IllegalStateException> { bootstrap("admin", "short-pass1").run(DefaultApplicationArguments()) }

        assertEquals(0, employeeRepository.count())
    }

    private fun bootstrap(
        username: String,
        password: String,
    ) = AdminBootstrap(employeeRepository, passwordEncoder, BootstrapAdminProperties(username, password))

    companion object {
        private const val PASSWORD = "first-admin-pass-123"
    }
}
