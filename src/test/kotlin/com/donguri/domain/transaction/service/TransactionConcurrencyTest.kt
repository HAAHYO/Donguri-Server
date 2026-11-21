package com.donguri.domain.transaction.service

import com.donguri.domain.careaction.repository.CareActionRepository
import com.donguri.domain.customer.entity.Customer
import com.donguri.domain.customer.entity.CustomerGrade
import com.donguri.domain.customer.repository.CustomerRepository
import com.donguri.domain.employee.entity.EmployeeRole
import com.donguri.domain.transaction.dto.request.SimulateTransactionRequest
import com.donguri.domain.transaction.repository.TransactionRepository
import com.donguri.global.exception.InsufficientBalanceException
import com.donguri.global.security.jwt.JwtTokenProvider
import org.hibernate.Hibernate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionConcurrencyTest(
    @Autowired private val transactionService: TransactionService,
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
    fun `동시 결제 요청에도 잔액이 음수가 되거나 갱신이 유실되지 않는다`() {
        val customer =
            customerRepository.save(
                Customer(
                    name = "동시성",
                    accountNumber = "110-999-000001",
                    balance = BigDecimal("500000"),
                    grade = CustomerGrade.GENERAL,
                    avgTxAmount = BigDecimal("100000"),
                    appLoginFreq = 10,
                    churnScore = BigDecimal("0.1000"),
                ),
            )
        val request =
            SimulateTransactionRequest(
                customerId = customer.id!!,
                amount = BigDecimal("100000"),
                occurredAt = OffsetDateTime.of(2026, 11, 21, 14, 0, 0, 0, ZoneOffset.ofHours(9)),
            )
        val requests = 10
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(requests)
        val futures =
            (1..requests).map {
                pool.submit<Result<Unit>> {
                    start.await()
                    runCatching { transactionService.simulate(request) }.map { }
                }
            }
        start.countDown()
        val results = futures.map { it.get(30, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals(5, results.count { it.isSuccess })
        assertEquals(5, results.count { it.exceptionOrNull() is InsufficientBalanceException })
        assertEquals(
            BigDecimal("0.00"),
            customerRepository
                .findById(customer.id!!)
                .get()
                .balance
                .setScale(2),
        )
        assertEquals(5, transactionRepository.count())
    }

    @Test
    fun `오탐 판정의 거래 락은 고객을 미리 읽지 않아 그 사이 커밋된 잔액을 덮어쓰지 않는다`() {
        val customer = saveCustomer("110-999-000002", "1000000")
        val blocked =
            transactionService.simulate(
                SimulateTransactionRequest(
                    customerId = customer.id!!,
                    amount = BigDecimal("900000"),
                    isOverseas = true,
                    deviceChanged = true,
                    occurredAt = OffsetDateTime.of(2026, 11, 21, 3, 0, 0, 0, ZoneOffset.ofHours(9)),
                ),
            )
        val pool = Executors.newSingleThreadExecutor()

        transactionTemplate.executeWithoutResult {
            val transaction = transactionRepository.findByIdForUpdate(blocked.transactionId)!!
            assertFalse(Hibernate.isInitialized(transaction.customer))
            pool
                .submit {
                    transactionService.simulate(
                        SimulateTransactionRequest(
                            customerId = customer.id!!,
                            amount = BigDecimal("100000"),
                            occurredAt = OffsetDateTime.of(2026, 11, 21, 14, 0, 0, 0, ZoneOffset.ofHours(9)),
                        ),
                    )
                }.get(10, TimeUnit.SECONDS)
            val locked = customerRepository.findByIdForUpdate(customer.id!!)!!
            assertEquals(0, BigDecimal("900000").compareTo(locked.balance))
        }
        pool.shutdown()
    }

    @Test
    fun `고객 락을 기다리다 시간이 초과되면 409 LOCK_CONFLICT 를 반환한다`() {
        val customer = saveCustomer("110-999-000003", "1000000")
        val token = jwtTokenProvider.createAccessToken("fds_admin", EmployeeRole.FDS_ADMIN)
        val pool = Executors.newSingleThreadExecutor()

        transactionTemplate.executeWithoutResult {
            customerRepository.findByIdForUpdate(customer.id!!)
            pool
                .submit {
                    mockMvc
                        .post("/api/transactions/simulate") {
                            contentType = MediaType.APPLICATION_JSON
                            content = """{"customerId":${customer.id},"amount":1000}"""
                            header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                        }.andExpect {
                            status { isConflict() }
                            jsonPath("$.code") { value("LOCK_CONFLICT") }
                        }
                }.get(15, TimeUnit.SECONDS)
        }
        pool.shutdown()
    }

    private fun saveCustomer(
        accountNumber: String,
        balance: String,
    ): Customer =
        customerRepository.save(
            Customer(
                name = "동시성",
                accountNumber = accountNumber,
                balance = BigDecimal(balance),
                grade = CustomerGrade.GENERAL,
                avgTxAmount = BigDecimal("100000"),
                appLoginFreq = 10,
                churnScore = BigDecimal("0.1000"),
            ),
        )
}
