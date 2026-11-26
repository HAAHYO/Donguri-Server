package com.safeflow.global.entity

import com.safeflow.domain.customer.entity.Customer
import com.safeflow.domain.customer.entity.CustomerGrade
import com.safeflow.domain.customer.repository.CustomerRepository
import com.safeflow.domain.transaction.entity.FdsStatus
import com.safeflow.domain.transaction.entity.Transaction
import com.safeflow.domain.transaction.repository.TransactionRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import kotlin.test.assertEquals

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class TimeZoneStorageTest(
    @Autowired private val em: EntityManager,
    @Autowired private val customerRepository: CustomerRepository,
    @Autowired private val transactionRepository: TransactionRepository,
) {
    @Test
    fun `테스트 JVM 의 기본 시간대는 앱과 같은 Asia_Seoul 이다`() {
        assertEquals("Asia/Seoul", TimeZone.getDefault().id)
    }

    @Test
    fun `LocalDateTime 은 시간대 변환 없이 DB 에 그대로 저장된다`() {
        val occurredAt = LocalDateTime.of(2026, 1, 1, 12, 0, 0)
        val transaction = transactionRepository.saveAndFlush(transaction(occurredAt))

        val row =
            em
                .createNativeQuery(
                    "select formatdatetime(occurred_at, 'yyyy-MM-dd HH:mm:ss'), formatdatetime(created_at, 'yyyy-MM-dd HH:mm') from transactions where id = :id",
                ).setParameter("id", transaction.id)
                .singleResult as Array<*>

        assertEquals("2026-01-01 12:00:00", row[0])
        assertEquals(transaction.createdAt.format(MINUTE), row[1])
    }

    private fun transaction(occurredAt: LocalDateTime) =
        Transaction(
            customer =
                customerRepository.saveAndFlush(
                    Customer(
                        name = "테스트",
                        accountNumber = "110-000-000001",
                        balance = BigDecimal("1000000.00"),
                        grade = CustomerGrade.GENERAL,
                        avgTxAmount = BigDecimal("50000.00"),
                        appLoginFreq = 10,
                        churnScore = BigDecimal("0.1000"),
                    ),
                ),
            amount = BigDecimal("100000.00"),
            txHour = 12,
            isOverseas = false,
            deviceChanged = false,
            fraudScore = BigDecimal("0.0500"),
            fdsStatus = FdsStatus.NORMAL,
            shapReasons = "[]",
            occurredAt = occurredAt,
        )

    companion object {
        private val MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
