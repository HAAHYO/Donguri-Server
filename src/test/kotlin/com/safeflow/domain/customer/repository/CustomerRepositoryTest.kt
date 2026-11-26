package com.safeflow.domain.customer.repository

import com.safeflow.domain.customer.entity.Customer
import com.safeflow.domain.customer.entity.CustomerGrade
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import kotlin.test.assertEquals

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CustomerRepositoryTest(
    @Autowired private val customerRepository: CustomerRepository,
) {
    @Test
    fun `고위험 고객은 오탐 피해자이거나 이탈 점수가 기준 이상인 고객이고 이탈 점수 내림차순이다`() {
        val belowThreshold = save("110-000-000001", "0.5999")
        val atThreshold = save("110-000-000002", "0.6000")
        val high = save("110-000-000003", "0.9100")
        val victimWithLowScore = save("110-000-000004", "0.1000", falsePositiveVictim = true)
        val sameScoreLater = save("110-000-000005", "0.6000")

        val ids = customerRepository.findHighRisk(BigDecimal("0.60")).map { it.id }

        assertEquals(listOf(high.id, atThreshold.id, sameScoreLater.id, victimWithLowScore.id), ids)
        assertEquals(false, belowThreshold.id in ids)
    }

    private fun save(
        accountNumber: String,
        churnScore: String,
        falsePositiveVictim: Boolean = false,
    ) = customerRepository.saveAndFlush(
        Customer(
            name = "테스트",
            accountNumber = accountNumber,
            balance = BigDecimal("1000000.00"),
            grade = CustomerGrade.GENERAL,
            avgTxAmount = BigDecimal("50000.00"),
            appLoginFreq = 10,
            churnScore = BigDecimal(churnScore),
            isFalsePositiveVictim = falsePositiveVictim,
        ),
    )
}
