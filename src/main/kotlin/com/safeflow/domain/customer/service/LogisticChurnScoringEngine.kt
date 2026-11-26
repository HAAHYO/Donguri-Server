package com.safeflow.domain.customer.service

import com.safeflow.domain.customer.entity.CustomerGrade
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.exp

@Component
class LogisticChurnScoringEngine : ChurnScoringEngine {
    override fun score(input: ChurnScoringInput): ChurnScoringResult {
        val deviations = features(input).map { it.coefficient * (it.value - it.baseline) }
        val probability = sigmoid(INTERCEPT + deviations.sum())
        return ChurnScoringResult(toScore(probability), emptyList())
    }

    private fun features(input: ChurnScoringInput): List<Feature> =
        listOf(
            Feature(RECENT_FALSE_POSITIVE_COUNT, input.recentFalsePositiveCount.coerceIn(0, 3).toDouble(), 0.0, 1.60),
            Feature(FALSE_POSITIVE_VICTIM, if (input.isFalsePositiveVictim) 1.0 else 0.0, 0.0, 0.60),
            Feature(RECENT_BLOCKED_COUNT, input.recentBlockedCount.coerceIn(0, 5).toDouble(), 0.0, 0.50),
            Feature(RECENT_WARNING_COUNT, input.recentWarningCount.coerceIn(0, 5).toDouble(), 0.0, 0.20),
            Feature(CARD_USAGE_CHANGE_RATE, input.cardUsageChangeRate.toDouble().coerceIn(-1.0, 1.0), 0.0, -2.00),
            Feature(LOGIN_FREQ_CHANGE_RATE, input.loginFreqChangeRate.toDouble().coerceIn(-1.0, 1.0), 0.0, -1.50),
            Feature(APP_LOGIN_FREQ, input.appLoginFreq.coerceIn(0, 60).toDouble(), 15.0, -0.04),
            Feature(VIP_GRADE, if (input.grade == CustomerGrade.VIP) 1.0 else 0.0, 0.0, -0.30),
        )

    private fun sigmoid(logOdds: Double): Double = 1.0 / (1.0 + exp(-logOdds))

    private fun toScore(probability: Double): BigDecimal = BigDecimal.valueOf(probability).setScale(SCALE, RoundingMode.HALF_UP)

    private data class Feature(
        val name: String,
        val value: Double,
        val baseline: Double,
        val coefficient: Double,
    )

    companion object {
        const val RECENT_FALSE_POSITIVE_COUNT = "recent_false_positive_count"
        const val FALSE_POSITIVE_VICTIM = "false_positive_victim"
        const val RECENT_BLOCKED_COUNT = "recent_blocked_count"
        const val RECENT_WARNING_COUNT = "recent_warning_count"
        const val CARD_USAGE_CHANGE_RATE = "card_usage_change_rate"
        const val LOGIN_FREQ_CHANGE_RATE = "login_freq_change_rate"
        const val APP_LOGIN_FREQ = "app_login_freq"
        const val VIP_GRADE = "vip_grade"

        private const val INTERCEPT = -2.20
        private const val SCALE = 4
    }
}
