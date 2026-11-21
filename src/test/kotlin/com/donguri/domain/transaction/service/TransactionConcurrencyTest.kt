package com.donguri.domain.transaction.service
import com.donguri.domain.careaction.repository.CareActionRepository
import com.donguri.domain.customer.entity.Customer
import com.donguri.domain.customer.entity.CustomerGrade
import com.donguri.domain.customer.repository.CustomerRepository
import com.donguri.domain.transaction.dto.request.SimulateTransactionRequest
import com.donguri.domain.transaction.repository.TransactionRepository
import com.donguri.global.exception.InsufficientBalanceException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

@SpringBootTest
@ActiveProfiles("test")
class TransactionConcurrencyTest(
    @Autowired private val transactionService: TransactionService,
    @Autowired private val customerRepository: CustomerRepository,
    @Autowired private val transactionRepository: TransactionRepository,
    @Autowired private val careActionRepository: CareActionRepository,
) {
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
                occurredAt = LocalDateTime.of(2026, 11, 21, 14, 0),
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
}
