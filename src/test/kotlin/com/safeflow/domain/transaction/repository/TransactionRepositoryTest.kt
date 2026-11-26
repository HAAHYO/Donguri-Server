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

    private fun customer() =
        Customer(
            name = "테스트",
            accountNumber = "110-000-000001",
            balance = BigDecimal("1000000.00"),
            grade = CustomerGrade.GENERAL,
            avgTxAmount = BigDecimal("50000.00"),
            appLoginFreq = 10,
            churnScore = BigDecimal("0.1000"),
        )

    private fun transaction(customer: Customer) =
        Transaction(
            customer = customer,
            amount = BigDecimal("100000.00"),
            txHour = 13,
            isOverseas = false,
            deviceChanged = false,
            fraudScore = BigDecimal("0.0500"),
            fdsStatus = FdsStatus.NORMAL,
            shapReasons = "[]",
        )
}
