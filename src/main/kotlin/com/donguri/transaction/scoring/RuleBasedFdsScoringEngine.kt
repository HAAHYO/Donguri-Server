package com.donguri.transaction.scoring

import com.donguri.transaction.FdsStatus
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

@Component
class RuleBasedFdsScoringEngine : FdsScoringEngine {
    override fun score(input: FdsScoringInput): FdsScoringResult {
        val contributions =
            listOf(
                ShapReason(AMOUNT_RATIO, amountRatioContribution(input.amount, input.avgTxAmount)),
                ShapReason(TX_HOUR, if (input.txHour in NIGHT_HOURS) NIGHT_WEIGHT else BigDecimal.ZERO),
                ShapReason(IS_OVERSEAS, if (input.isOverseas) OVERSEAS_WEIGHT else BigDecimal.ZERO),
                ShapReason(DEVICE_CHANGED, if (input.deviceChanged) DEVICE_WEIGHT else BigDecimal.ZERO),
            ).map { it.copy(shap = it.shap.setScale(SCALE, RoundingMode.HALF_UP)) }

        val fraudScore =
            contributions
                .fold(BigDecimal.ZERO) { sum, reason -> sum + reason.shap }
                .min(BigDecimal.ONE)
                .setScale(SCALE, RoundingMode.HALF_UP)
        val topReasons =
            contributions
                .filter { it.shap.signum() > 0 }
                .sortedByDescending { it.shap }
                .take(TOP_REASONS)
        return FdsScoringResult(fraudScore, FdsStatus.of(fraudScore), topReasons)
    }

    private fun amountRatioContribution(
        amount: BigDecimal,
        avgTxAmount: BigDecimal,
    ): BigDecimal {
        if (avgTxAmount.signum() <= 0) return AMOUNT_WEIGHT
        val ratio = amount.divide(avgTxAmount, RATIO_SCALE, RoundingMode.HALF_UP)
        val excess = (ratio - BigDecimal.ONE).max(BigDecimal.ZERO).min(AMOUNT_SATURATION)
        return AMOUNT_WEIGHT * excess.divide(AMOUNT_SATURATION, RATIO_SCALE, RoundingMode.HALF_UP)
    }

    companion object {
        const val AMOUNT_RATIO = "amount_ratio"
        const val TX_HOUR = "tx_hour"
        const val IS_OVERSEAS = "is_overseas"
        const val DEVICE_CHANGED = "device_changed"

        private val AMOUNT_WEIGHT = BigDecimal("0.40")
        private val AMOUNT_SATURATION = BigDecimal("4")
        private val NIGHT_WEIGHT = BigDecimal("0.20")
        private val OVERSEAS_WEIGHT = BigDecimal("0.25")
        private val DEVICE_WEIGHT = BigDecimal("0.30")
        private val NIGHT_HOURS = 0..5
        private const val TOP_REASONS = 3
        private const val SCALE = 4
        private const val RATIO_SCALE = 6
    }
}
