package com.safeflow.domain.transaction.repository

import com.safeflow.domain.customer.entity.Customer
import com.safeflow.domain.customer.entity.CustomerGrade
import com.safeflow.domain.customer.repository.CustomerRepository
import com.safeflow.domain.transaction.entity.FdsStatus
import com.safeflow.domain.transaction.entity.Transaction
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class TransactionRepositoryTest(
    @Autowired private val em: EntityManager,
    @Autowired private val customerRepository: CustomerRepository,
    @Autowired private val transactionRepository: TransactionRepository,
) {
    @Test
    fun `거래 목록은 id 가 아니라 생성 시각 최신순이고 같은 시각이면 id 가 큰 것이 먼저다`() {
        val customer = customerRepository.saveAndFlush(customer())
        val first = transactionRepository.saveAndFlush(transaction(customer))
        val second = transactionRepository.saveAndFlush(transaction(customer))
        val third = transactionRepository.saveAndFlush(transaction(customer))
        setCreatedAt(first, LocalDateTime.of(2026, 1, 1, 12, 0, 0))
        setCreatedAt(second, LocalDateTime.of(2026, 1, 1, 10, 0, 0))
        setCreatedAt(third, LocalDateTime.of(2026, 1, 1, 10, 0, 0))
        em.clear()

        val ids = transactionRepository.findAllWithCustomer().map { it.id }

        assertEquals(listOf(first.id, third.id, second.id), ids)
    }

    @Test
    fun `최근 건수는 기간 안의 해당 고객 거래만 경고·차단·오탐으로 센다`() {
        val customer = customerRepository.saveAndFlush(customer())
        val other = customerRepository.saveAndFlush(customer("110-000-000002"))
        transactionRepository.save(transaction(customer, FdsStatus.NORMAL))
        transactionRepository.save(transaction(customer, FdsStatus.WARNING))
        transactionRepository.save(transaction(customer, FdsStatus.BLOCKED))
        transactionRepository.save(transaction(customer, FdsStatus.BLOCKED).also { it.isActualFraud = false })
        val old = transactionRepository.save(transaction(customer, FdsStatus.WARNING))
        transactionRepository.save(transaction(other, FdsStatus.BLOCKED))
        transactionRepository.flush()
        setCreatedAt(old, LocalDateTime.now().minusDays(40))

        val counts = transactionRepository.countRecentFds(customer.id!!, LocalDateTime.now().minusDays(30))

        assertEquals(RecentFdsCounts(warning = 1, blocked = 2, falsePositive = 1), counts)
    }

    @Test
    fun `실제 사기가 아니었다고 기록돼도 차단되지 않은 거래는 오탐으로 세지 않는다`() {
        val customer = customerRepository.saveAndFlush(customer())
        transactionRepository.save(transaction(customer, FdsStatus.NORMAL).also { it.isActualFraud = false })
        transactionRepository.save(transaction(customer, FdsStatus.WARNING).also { it.isActualFraud = false })
        transactionRepository.flush()

        val counts = transactionRepository.countRecentFds(customer.id!!, LocalDateTime.now().minusDays(30))

        assertEquals(RecentFdsCounts(warning = 1, blocked = 0, falsePositive = 0), counts)
    }

    @Test
    fun `기간 안에 거래가 없으면 모든 건수가 0 이다`() {
        val customer = customerRepository.saveAndFlush(customer())

        assertEquals(RecentFdsCounts(0, 0, 0), transactionRepository.countRecentFds(customer.id!!, LocalDateTime.now().minusDays(30)))
    }

    private fun setCreatedAt(
        transaction: Transaction,
        createdAt: LocalDateTime,
    ) {
        em
            .createNativeQuery("update transactions set created_at = :createdAt where id = :id")
            .setParameter("createdAt", createdAt)
            .setParameter("id", transaction.id)
            .executeUpdate()
    }

    private fun customer(accountNumber: String = "110-000-000001") =
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
        status: FdsStatus = FdsStatus.NORMAL,
    ) = Transaction(
        customer = customer,
        amount = BigDecimal("100000.00"),
        txHour = 13,
        isOverseas = false,
        deviceChanged = false,
        fraudScore = BigDecimal("0.0500"),
        fdsStatus = status,
        shapReasons = "[]",
    )
}
