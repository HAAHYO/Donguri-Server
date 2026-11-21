package com.donguri.domain.careaction.service

import com.donguri.domain.careaction.entity.CareAction
import com.donguri.domain.careaction.entity.CareActionStatus
import com.donguri.domain.careaction.repository.CareActionRepository
import com.donguri.domain.customer.entity.Customer
import com.donguri.domain.customer.entity.CustomerGrade
import com.donguri.domain.customer.repository.CustomerRepository
import com.donguri.domain.employee.entity.EmployeeRole
import com.donguri.domain.transaction.entity.FdsStatus
import com.donguri.domain.transaction.entity.Transaction
import com.donguri.domain.transaction.repository.TransactionRepository
import com.donguri.global.exception.CareActionAlreadySentException
import com.donguri.global.security.jwt.JwtTokenProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.patch
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CareActionConcurrencyTest(
    @Autowired private val careActionService: CareActionService,
    @Autowired private val customerRepository: CustomerRepository,
    @Autowired private val transactionRepository: TransactionRepository,
    @Autowired private val careActionRepository: CareActionRepository,
    @Autowired private val transactionManager: PlatformTransactionManager,
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jwtTokenProvider: JwtTokenProvider,
) {
    private val transactionTemplate = TransactionTemplate(transactionManager)

    @AfterEach
    fun cleanUp() {
        careActionRepository.deleteAll()
        transactionRepository.deleteAll()
        customerRepository.deleteAll()
    }

    @Test
    fun `동시에 발송해도 한 건만 성공하고 나머지는 이미 발송됨으로 실패한다`() {
        val careAction = saveCareAction("110-222-000001")
        val requests = 5
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(requests)
        val futures =
            (1..requests).map { index ->
                pool.submit<Result<Unit>> {
                    start.await()
                    runCatching { careActionService.send(careAction.id!!, "crm_$index") }.map { }
                }
            }
        start.countDown()
        val results = futures.map { it.get(30, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals(1, results.count { it.isSuccess })
        assertEquals(requests - 1, results.count { it.exceptionOrNull() is CareActionAlreadySentException })
        val stored = careActionRepository.findById(careAction.id!!).get()
        assertEquals(CareActionStatus.SENT, stored.status)
        assertTrue(stored.sentBy!!.startsWith("crm_"))
    }

    @Test
    fun `케어 조치 락을 기다리다 시간이 초과되면 409 LOCK_CONFLICT 를 반환한다`() {
        val careAction = saveCareAction("110-222-000002")
        val token = jwtTokenProvider.createAccessToken("crm_marketer", EmployeeRole.CRM_MARKETER)
        val pool = Executors.newSingleThreadExecutor()

        transactionTemplate.executeWithoutResult {
            careActionRepository.findByIdForUpdate(careAction.id!!)
            pool
                .submit {
                    mockMvc
                        .patch("/api/care-actions/${careAction.id}/send") {
                            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        }.andExpect {
                            status { isConflict() }
                            jsonPath("$.code") { value("LOCK_CONFLICT") }
                        }
                }.get(15, TimeUnit.SECONDS)
        }
        pool.shutdown()
    }

    private fun saveCareAction(accountNumber: String): CareAction {
        val customer =
            customerRepository.save(
                Customer(
                    name = "동시성",
                    accountNumber = accountNumber,
                    balance = BigDecimal("1000000.00"),
                    grade = CustomerGrade.GENERAL,
                    avgTxAmount = BigDecimal("100000.00"),
                    appLoginFreq = 10,
                    churnScore = BigDecimal("0.5000"),
                ),
            )
        val transaction =
            transactionRepository.save(
                Transaction(
                    customer = customer,
                    amount = BigDecimal("900000.00"),
                    txHour = 3,
                    isOverseas = true,
                    deviceChanged = true,
                    fraudScore = BigDecimal("0.9000"),
                    fdsStatus = FdsStatus.BLOCKED,
                    shapReasons = "[]",
                ),
            )
        return careActionRepository.save(CareAction(transaction, "우대금리 파킹통장", "불편을 드려 죄송합니다."))
    }
}
