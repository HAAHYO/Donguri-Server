package com.safeflow.domain.customer.service

import com.safeflow.domain.customer.entity.CustomerGrade
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogisticChurnScoringEngineTest {
    private val engine = LogisticChurnScoringEngine()

    @Test
    fun `기준 고객의 이탈 확률은 절편의 sigmoid 이다`() {
        assertEquals(BigDecimal("0.0998"), engine.score(input()).churnScore)
    }

    @Test
    fun `피처는 계수만큼 로그 오즈를 바꾼다`() {
        assertEquals(BigDecimal("0.5000"), engine.score(input(recentFalsePositiveCount = 1, isFalsePositiveVictim = true)).churnScore)
        assertEquals(BigDecimal("0.2315"), engine.score(input(cardUsageChangeRate = "-0.5000")).churnScore)
        assertEquals(BigDecimal("0.0630"), engine.score(input(grade = CustomerGrade.VIP, appLoginFreq = 20)).churnScore)
    }

    @Test
    fun `범위를 벗어난 피처는 경계값으로 잘린다`() {
        assertEquals(engine.score(input(recentFalsePositiveCount = 3)), engine.score(input(recentFalsePositiveCount = 10)))
        assertEquals(engine.score(input(cardUsageChangeRate = "-1.0000")), engine.score(input(cardUsageChangeRate = "-5.0000")))
        assertEquals(engine.score(input(appLoginFreq = 0)), engine.score(input(appLoginFreq = -3)))
    }

    @Test
    fun `차단·경고가 늘수록 이탈 확률이 오르고 1 을 넘지 않는다`() {
        val scores = (0..5).map { engine.score(input(recentBlockedCount = it, recentWarningCount = it)).churnScore }

        assertEquals(scores.sorted(), scores)
        assertTrue(scores.zipWithNext().all { (a, b) -> a < b })
        val worst =
            engine.score(
                input(
                    recentFalsePositiveCount = 3,
                    isFalsePositiveVictim = true,
                    recentBlockedCount = 5,
                    recentWarningCount = 5,
                    cardUsageChangeRate = "-1.0000",
                    loginFreqChangeRate = "-1.0000",
                    appLoginFreq = 0,
                ),
            )
        assertTrue(worst.churnScore <= BigDecimal.ONE)
    }

    private fun input(
        recentFalsePositiveCount: Int = 0,
        isFalsePositiveVictim: Boolean = false,
        recentBlockedCount: Int = 0,
        recentWarningCount: Int = 0,
        cardUsageChangeRate: String = "0.0000",
        loginFreqChangeRate: String = "0.0000",
        appLoginFreq: Int = 15,
        grade: CustomerGrade = CustomerGrade.GENERAL,
    ) = ChurnScoringInput(
        recentFalsePositiveCount = recentFalsePositiveCount,
        isFalsePositiveVictim = isFalsePositiveVictim,
        recentBlockedCount = recentBlockedCount,
        recentWarningCount = recentWarningCount,
        cardUsageChangeRate = BigDecimal(cardUsageChangeRate),
        loginFreqChangeRate = BigDecimal(loginFreqChangeRate),
        appLoginFreq = appLoginFreq,
        grade = grade,
    )
}
