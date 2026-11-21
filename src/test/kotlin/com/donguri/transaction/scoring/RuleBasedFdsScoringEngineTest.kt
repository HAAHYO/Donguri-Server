package com.donguri.transaction.scoring

import com.donguri.transaction.FdsStatus
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuleBasedFdsScoringEngineTest {
    private val engine = RuleBasedFdsScoringEngine()

    private fun score(
        amount: String = "100000",
        avg: String = "100000",
        hour: Int = 14,
        overseas: Boolean = false,
        device: Boolean = false,
    ) = engine.score(FdsScoringInput(BigDecimal(amount), BigDecimal(avg), hour, overseas, device))

    @Test
    fun `위험 요소가 없으면 0점 NORMAL 이고 근거가 없다`() {
        val result = score()

        assertEquals(BigDecimal("0.0000"), result.fraudScore)
        assertEquals(FdsStatus.NORMAL, result.status)
        assertEquals(emptyList(), result.topReasons)
    }

    @Test
    fun `피처별 기여도는 규칙 가중치와 같다`() {
        assertEquals(BigDecimal("0.2000"), score(hour = 3).fraudScore)
        assertEquals(BigDecimal("0.0000"), score(hour = 6).fraudScore)
        assertEquals(BigDecimal("0.2500"), score(overseas = true).fraudScore)
        assertEquals(BigDecimal("0.3000"), score(device = true).fraudScore)
        assertEquals(BigDecimal("0.2000"), score(amount = "300000").fraudScore)
        assertEquals(BigDecimal("0.4000"), score(amount = "500000").fraudScore)
        assertEquals(BigDecimal("0.4000"), score(amount = "9000000").fraudScore)
        assertEquals(BigDecimal("0.0000"), score(amount = "50000").fraudScore)
        assertEquals(BigDecimal("0.4000"), score(avg = "0").fraudScore)
    }

    @Test
    fun `점수 구간에 따라 상태가 정해진다`() {
        assertEquals(FdsStatus.NORMAL, FdsStatus.of(BigDecimal("0.4999")))
        assertEquals(FdsStatus.WARNING, FdsStatus.of(BigDecimal("0.5000")))
        assertEquals(FdsStatus.WARNING, FdsStatus.of(BigDecimal("0.7999")))
        assertEquals(FdsStatus.BLOCKED, FdsStatus.of(BigDecimal("0.8000")))
        assertEquals(FdsStatus.WARNING, score(overseas = true, device = true).status)
        assertEquals(FdsStatus.BLOCKED, score(amount = "500000", device = true, hour = 2).status)
    }

    @Test
    fun `점수는 1 을 넘지 않는다`() {
        assertEquals(BigDecimal("1.0000"), score(amount = "900000", hour = 1, overseas = true, device = true).fraudScore)
    }

    @Test
    fun `근거는 기여도가 큰 순서로 최대 3개이고 0 인 피처는 빠진다`() {
        val result = score(amount = "300000", hour = 1, overseas = true, device = true)

        assertEquals(listOf("device_changed", "is_overseas", "amount_ratio"), result.topReasons.map { it.feature })
        assertEquals(3, result.topReasons.size)
        assertTrue(result.topReasons.zipWithNext().all { (a, b) -> a.shap >= b.shap })
        assertEquals(listOf("is_overseas"), score(overseas = true).topReasons.map { it.feature })
    }
}
