package com.safeflow.domain.customer.service

import com.safeflow.domain.customer.entity.Customer
import com.safeflow.domain.customer.entity.CustomerGrade
import com.safeflow.domain.customer.repository.CustomerRepository
import com.safeflow.domain.transaction.entity.FdsStatus
import com.safeflow.domain.transaction.entity.Transaction
import com.safeflow.domain.transaction.repository.TransactionRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import kotlin.test.assertEquals

@SpringBootTest
@ActiveProfiles("test")
class ChurnServiceTest(
    @Autowired private val churnService: ChurnService,
    @Autowired private val customerRepository: CustomerRepository,
    @Autowired private val transactionRepository: TransactionRepository,
    @Autowired private val jsonMapper: JsonMapper,
) {
    @Test
    @Transactional
    fun `최근 거래 건수와 고객 활동 지표로 이탈 점수와 근거를 다시 계산해 고객에 저장한다`() {
        val customer = customerRepository.save(customer())
        transactionRepository.save(transaction(customer, FdsStatus.BLOCKED, isActualFraud = false))
        transactionRepository.save(transaction(customer, FdsStatus.BLOCKED))
        transactionRepository.save(transaction(customer, FdsStatus.WARNING))
        transactionRepository.flush()
        val expected =
            LogisticChurnScoringEngine().score(
                ChurnScoringInput(
                    recentFalsePositiveCount = 1,
                    isFalsePositiveVictim = true,
                    recentBlockedCount = 2,
                    recentWarningCount = 1,
                    cardUsageChangeRate = BigDecimal("-0.4000"),
                    loginFreqChangeRate = BigDecimal("-0.2000"),
                    appLoginFreq = 8,
                    grade = CustomerGrade.GOLD,
                ),
            )

        churnService.recalculate(customer)

        assertEquals(expected.churnScore, customer.churnScore)
        assertEquals(jsonMapper.writeValueAsString(expected.topReasons), customer.churnReasons)
        assertEquals(3, expected.topReasons.size)
    }

    @Test
    fun `고객 락을 잡은 트랜잭션 밖에서는 호출할 수 없다`() {
        assertThrows<IllegalTransactionStateException> { churnService.recalculate(customer()) }
    }

    private fun customer() =
        Customer(
            name = "테스트",
            accountNumber = "110-000-000001",
            balance = BigDecimal("1000000.00"),
            grade = CustomerGrade.GOLD,
            avgTxAmount = BigDecimal("50000.00"),
            appLoginFreq = 8,
            churnScore = BigDecimal("0.1000"),
            isFalsePositiveVictim = true,
            cardUsageChangeRate = BigDecimal("-0.4000"),
            loginFreqChangeRate = BigDecimal("-0.2000"),
        )

    private fun transaction(
        customer: Customer,
        status: FdsStatus,
        isActualFraud: Boolean? = null,
    ) = Transaction(
        customer = customer,
        amount = BigDecimal("100000.00"),
        txHour = 13,
        isOverseas = false,
        deviceChanged = false,
        fraudScore = BigDecimal("0.9000"),
        fdsStatus = status,
        isActualFraud = isActualFraud,
        shapReasons = "[]",
    )
}
