package com.safeflow.domain.careaction.service

import com.safeflow.domain.careaction.entity.CareAction
import com.safeflow.domain.careaction.entity.CareActionSendHistory
import com.safeflow.domain.careaction.entity.CareActionStatus
import com.safeflow.domain.careaction.repository.CareActionRepository
import com.safeflow.domain.careaction.repository.CareActionSendHistoryRepository
import com.safeflow.domain.customer.entity.Customer
import com.safeflow.domain.customer.entity.CustomerGrade
import com.safeflow.domain.customer.repository.CustomerRepository
import com.safeflow.domain.transaction.entity.FdsStatus
import com.safeflow.domain.transaction.entity.Transaction
import com.safeflow.domain.transaction.repository.TransactionRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest
@ActiveProfiles("test")
@Import(FailingSendHistoryTestConfig::class)
class CareActionSendAtomicityTest(
    @Autowired private val careActionService: CareActionService,
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
    fun `이력 저장이 실패하면 발송 상태도 커밋되지 않는다`() {
        val customer =
            customerRepository.save(
                Customer(
                    name = "원자성",
                    accountNumber = "110-333-000001",
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
        val careAction = careActionRepository.save(CareAction(transaction, "우대금리 파킹통장", "불편을 드려 죄송합니다."))

        assertThrows<IllegalStateException> { careActionService.send(careAction.id!!, 0, "crm_marketer") }

        val stored = careActionRepository.findById(careAction.id!!).get()
        assertEquals(CareActionStatus.PENDING, stored.status)
        assertNull(stored.sentBy)
        assertEquals(0, stored.version)
    }
}

@TestConfiguration
class FailingSendHistoryTestConfig {
    @Bean
    @Primary
    fun failingSendHistoryRepository(): CareActionSendHistoryRepository =
        object : CareActionSendHistoryRepository {
            override fun save(history: CareActionSendHistory): CareActionSendHistory = throw IllegalStateException("이력 저장 실패")

            override fun findAllByCareActionIdOrderByCreatedAtDescIdDesc(careActionId: Long): List<CareActionSendHistory> = emptyList()
        }
}
