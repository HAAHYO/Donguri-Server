package com.donguri.global.init

import com.donguri.domain.customer.repository.CustomerRepository
import com.donguri.domain.employee.repository.EmployeeRepository
import com.donguri.domain.transaction.entity.FdsStatus
import com.donguri.domain.transaction.repository.TransactionRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@ActiveProfiles("test", "local")
@Transactional
class DataInitializerTest(
    @Autowired private val dataInitializer: DataInitializer,
    @Autowired private val employeeRepository: EmployeeRepository,
    @Autowired private val customerRepository: CustomerRepository,
    @Autowired private val transactionRepository: TransactionRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
) {
    @Test
    fun `빈 DB 로 기동하면 직원 2명, 고객 10명, 거래 15건이 저장된다`() {
        assertEquals(setOf("fds_admin", "crm_marketer"), employeeRepository.findAll().map { it.username }.toSet())
        assertEquals(10, customerRepository.count())
        assertEquals(15, transactionRepository.count())
    }

    @Test
    fun `다시 실행해도 데이터가 중복되지 않는다`() {
        dataInitializer.run(DefaultApplicationArguments())

        assertEquals(2, employeeRepository.count())
        assertEquals(10, customerRepository.count())
        assertEquals(15, transactionRepository.count())
    }

    @Test
    fun `직원 비밀번호는 평문이 아니라 설정된 시드 비밀번호의 bcrypt 해시로 저장된다`() {
        employeeRepository.findAll().forEach {
            assertTrue(it.password.startsWith("{bcrypt}"), "${it.username} 비밀번호가 bcrypt 해시가 아니다")
            assertTrue(passwordEncoder.matches("test-seed-password", it.password))
        }
    }

    @Test
    fun `거래의 FDS 상태는 사기 점수 구간과 일치하고 SHAP 사유는 JSON 배열이다`() {
        val json = JsonMapper.builder().build()

        transactionRepository.findAll().forEach {
            val expected =
                when {
                    it.fraudScore >= BigDecimal("0.80") -> FdsStatus.BLOCKED
                    it.fraudScore >= BigDecimal("0.50") -> FdsStatus.WARNING
                    else -> FdsStatus.NORMAL
                }
            assertEquals(expected, it.fdsStatus, "거래 ${it.id} 점수 ${it.fraudScore}")
            assertTrue(json.readTree(it.shapReasons).isArray, "거래 ${it.id} shapReasons 가 JSON 배열이 아니다")
        }
    }

    @Test
    fun `오탐 피해 고객은 모두 실제 사기가 아닌데 차단된 거래를 가진다`() {
        val falsePositiveCustomerIds =
            transactionRepository
                .findAll()
                .filter { it.fdsStatus == FdsStatus.BLOCKED && it.isActualFraud == false }
                .map { it.customer.id }
                .toSet()
        val victims =
            customerRepository
                .findAll()
                .filter { it.isFalsePositiveVictim }
                .map { it.id }
                .toSet()

        assertTrue(victims.isNotEmpty())
        assertEquals(victims, falsePositiveCustomerIds)
    }
}
