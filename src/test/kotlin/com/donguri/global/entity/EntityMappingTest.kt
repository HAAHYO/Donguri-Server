package com.donguri.global.entity

import com.donguri.careaction.CareAction
import com.donguri.careaction.CareActionRepository
import com.donguri.customer.Customer
import com.donguri.customer.CustomerGrade
import com.donguri.customer.CustomerRepository
import com.donguri.employee.Employee
import com.donguri.employee.EmployeeRepository
import com.donguri.employee.EmployeeRole
import com.donguri.transaction.FdsStatus
import com.donguri.transaction.Transaction
import com.donguri.transaction.TransactionRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 임의 내장 DB 로 바꾸지 않고 test 프로필의 H2(MySQL 모드)를 그대로 쓴다 — 운영 DB 와 같은 방언으로 DDL 을 검증
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class EntityMappingTest(
    @Autowired private val em: EntityManager,
    @Autowired private val employeeRepository: EmployeeRepository,
    @Autowired private val customerRepository: CustomerRepository,
    @Autowired private val transactionRepository: TransactionRepository,
    @Autowired private val careActionRepository: CareActionRepository,
) {
    @Test
    fun `저장하면 생성·수정 시각이 기록되고 수정하면 수정 시각만 바뀐다`() {
        val customer = customerRepository.saveAndFlush(customer("110-000-000001"))
        val createdAt = customer.createdAt
        val firstUpdatedAt = customer.updatedAt

        Thread.sleep(20)
        customer.churnScore = BigDecimal("0.9000")
        customerRepository.saveAndFlush(customer)

        assertEquals(createdAt, customer.createdAt)
        assertTrue(customer.updatedAt.isAfter(firstUpdatedAt), "updatedAt 이 갱신되어야 한다")
    }

    @Test
    fun `Enum 은 이름 문자열로 저장된다`() {
        val employee = employeeRepository.saveAndFlush(Employee("fds_admin", "{bcrypt}x", EmployeeRole.FDS_ADMIN, "이상거래탐지팀"))

        val stored = em.createNativeQuery("select role from employee where id = :id").setParameter("id", employee.id).singleResult

        assertEquals("FDS_ADMIN", stored)
    }

    @Test
    fun `Enum 컬럼은 DB 네이티브 enum 이 아니라 문자열 타입이다`() {
        // 네이티브 enum 이면 상수를 추가해도 ddl-auto update 가 컬럼을 바꾸지 않아 새 값 저장이 실패한다
        val types =
            em
                .createNativeQuery(
                    "select lower(column_name), data_type from information_schema.columns " +
                        "where lower(table_name) in ('employee', 'customer', 'transactions', 'care_action') " +
                        "and lower(column_name) in ('role', 'grade', 'fds_status', 'status')",
                ).resultList
                .map { (it as Array<*>)[0] to it[1].toString().uppercase() }

        assertEquals(4, types.size, "Enum 컬럼 4개를 모두 찾아야 한다: $types")
        types.forEach { (column, type) -> assertEquals("CHARACTER VARYING", type, "$column 컬럼 타입") }
    }

    @Test
    fun `같은 계좌번호의 고객은 저장할 수 없다`() {
        customerRepository.saveAndFlush(customer("110-000-000009"))

        assertThrows<DataIntegrityViolationException> {
            customerRepository.saveAndFlush(customer("110-000-000009"))
        }
    }

    @Test
    fun `같은 username 의 직원은 저장할 수 없다`() {
        employeeRepository.saveAndFlush(Employee("crm_marketer", "{bcrypt}x", EmployeeRole.CRM_MARKETER, "고객관리팀"))

        assertThrows<DataIntegrityViolationException> {
            employeeRepository.saveAndFlush(Employee("crm_marketer", "{bcrypt}y", EmployeeRole.FDS_ADMIN, "다른팀"))
        }
    }

    @Test
    fun `한 거래에는 케어 조치를 하나만 만들 수 있다`() {
        val customer = customerRepository.save(customer("110-000-000002"))
        val transaction = transactionRepository.saveAndFlush(transaction(customer, "[]"))
        careActionRepository.saveAndFlush(CareAction(transaction, "우대금리 적금", "불편을 드려 죄송합니다."))

        assertThrows<DataIntegrityViolationException> {
            careActionRepository.saveAndFlush(CareAction(transaction, "수수료 면제", "다시 안내드립니다."))
        }
    }

    @Test
    fun `케어 조치의 고객은 항상 거래의 고객으로 저장된다`() {
        val customer = customerRepository.save(customer("110-000-000004"))
        val transaction = transactionRepository.save(transaction(customer, "[]"))
        val saved = careActionRepository.saveAndFlush(CareAction(transaction, "우대금리 적금", "불편을 드려 죄송합니다."))
        em.clear()

        val storedCustomerId =
            em
                .createNativeQuery("select customer_id from care_action where id = :id")
                .setParameter("id", saved.id)
                .singleResult

        assertEquals(customer.id, (storedCustomerId as Number).toLong())
        // 로딩(기본 생성자 경로)에서도 연관관계가 채워지는지
        assertEquals(
            customer.id,
            careActionRepository
                .findById(saved.id!!)
                .get()
                .customer.id,
        )
    }

    @Test
    fun `긴 SHAP JSON 도 잘리지 않고 저장되며 실제 사기 여부는 null 로 둘 수 있다`() {
        val customer = customerRepository.save(customer("110-000-000003"))
        val longJson = (1..300).joinToString(prefix = "[", postfix = "]") { """{"feature":"f$it","shap":0.001}""" }
        val saved = transactionRepository.saveAndFlush(transaction(customer, longJson))
        em.clear()

        val found = transactionRepository.findById(saved.id!!).get()

        assertEquals(longJson, found.shapReasons)
        assertNull(found.isActualFraud)
    }

    private fun customer(accountNumber: String) =
        Customer(
            name = "테스트",
            accountNumber = accountNumber,
            balance = BigDecimal("1000000.00"),
            grade = CustomerGrade.GENERAL,
            avgTxAmount = BigDecimal("50000.00"),
            appLoginFreq = 10,
            churnScore = BigDecimal("0.1000"),
        )

    private fun transaction(
        customer: Customer,
        shapReasons: String,
    ) = Transaction(
        customer = customer,
        amount = BigDecimal("100000.00"),
        txHour = 13,
        isOverseas = false,
        deviceChanged = false,
        fraudScore = BigDecimal("0.0500"),
        fdsStatus = FdsStatus.NORMAL,
        shapReasons = shapReasons,
    )
}
