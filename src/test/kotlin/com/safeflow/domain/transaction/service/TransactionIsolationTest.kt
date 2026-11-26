package com.safeflow.domain.transaction.service

import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals

class TransactionIsolationTest {
    @Test
    fun `이탈 점수를 다시 계산하는 트랜잭션은 커밋된 거래를 모두 보도록 READ COMMITTED 로 실행한다`() {
        listOf("simulate", "resolveFalsePositive").forEach { name ->
            val method = TransactionService::class.java.methods.single { it.name == name }

            assertEquals(Isolation.READ_COMMITTED, method.getAnnotation(Transactional::class.java).isolation, name)
        }
    }
}
