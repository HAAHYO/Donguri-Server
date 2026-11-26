package com.safeflow.domain.careaction.repository

import com.safeflow.domain.careaction.entity.CareAction
import com.safeflow.domain.careaction.entity.CareActionStatus
import com.safeflow.domain.customer.entity.Customer
import com.safeflow.domain.customer.entity.CustomerGrade
import com.safeflow.domain.customer.repository.CustomerRepository
import com.safeflow.domain.transaction.entity.FdsStatus
import com.safeflow.domain.transaction.entity.Transaction
import com.safeflow.domain.transaction.repository.TransactionRepository
import jakarta.persistence.EntityManager
import org.hibernate.Hibernate
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CareActionRepositoryTest(
    @Autowired private val em: EntityManager,
    @Autowired private val customerRepository: CustomerRepository,
    @Autowired private val transactionRepository: TransactionRepository,
    @Autowired private val careActionRepository: CareActionRepository,
) {
    @Test
    fun `케어 액션 목록은 id 가 아니라 생성 시각 최신순이고 같은 시각이면 id 가 큰 것이 먼저다`() {
        val customer = customerRepository.saveAndFlush(customer())
        val first = careActionRepository.saveAndFlush(careAction(customer))
        val second = careActionRepository.saveAndFlush(careAction(customer))
        val third = careActionRepository.saveAndFlush(careAction(customer))
        setCreatedAt(first, LocalDateTime.of(2026, 1, 1, 12, 0, 0))
        setCreatedAt(second, LocalDateTime.of(2026, 1, 1, 10, 0, 0))
        setCreatedAt(third, LocalDateTime.of(2026, 1, 1, 10, 0, 0))
        em.clear()

        val ids = careActionRepository.findAllWithCustomer().map { it.id }

        assertEquals(listOf(first.id, third.id, second.id), ids)
    }

    @Test
    fun `고객별 조회는 지정한 고객들의 PENDING 케어 조치만 거래와 함께 마지막으로 계획된 순서로 돌려준다`() {
        val target = customerRepository.saveAndFlush(customer("110-000-000001"))
        val other = customerRepository.saveAndFlush(customer("110-000-000002"))
        val replanned = careActionRepository.saveAndFlush(careAction(target))
        val warning = careActionRepository.saveAndFlush(careAction(target))
        val sent = careActionRepository.saveAndFlush(careAction(target).also { it.markSent("crm_marketer", 0) })
        careActionRepository.saveAndFlush(careAction(other))
        setTimes(replanned, created = LocalDateTime.of(2026, 1, 1, 9, 0, 0), updated = LocalDateTime.of(2026, 1, 1, 13, 0, 0))
        setTimes(warning, created = LocalDateTime.of(2026, 1, 1, 12, 0, 0), updated = LocalDateTime.of(2026, 1, 1, 12, 0, 0))
        em.clear()

        val found = careActionRepository.findAllWithTransactionByCustomerIdInAndStatus(listOf(target.id!!), CareActionStatus.PENDING)

        assertEquals(listOf(replanned.id, warning.id), found.map { it.id })
        assertEquals(false, sent.id in found.map { it.id })
        assertTrue(found.all { Hibernate.isInitialized(it.transaction) })
    }

    private fun setTimes(
        careAction: CareAction,
        created: LocalDateTime,
        updated: LocalDateTime,
    ) {
        em
            .createNativeQuery("update care_action set created_at = :created, updated_at = :updated where id = :id")
            .setParameter("created", created)
            .setParameter("updated", updated)
            .setParameter("id", careAction.id)
            .executeUpdate()
    }

    private fun setCreatedAt(
        careAction: CareAction,
        createdAt: LocalDateTime,
    ) {
        em
            .createNativeQuery("update care_action set created_at = :createdAt where id = :id")
            .setParameter("createdAt", createdAt)
            .setParameter("id", careAction.id)
            .executeUpdate()
    }

    private fun careAction(customer: Customer) =
        CareAction(
            transaction = transactionRepository.saveAndFlush(transaction(customer)),
            recommendedProduct = "우대 적금",
            actionMessage = "안내 메시지",
        )

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
