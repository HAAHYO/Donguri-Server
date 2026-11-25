package com.safeflow.domain.transaction.service

import com.safeflow.domain.transaction.entity.FdsStatus
import java.math.BigDecimal

interface FdsScoringEngine {
    fun score(input: FdsScoringInput): FdsScoringResult
}

data class FdsScoringInput(
    val amount: BigDecimal,
    val avgTxAmount: BigDecimal,
    val txHour: Int,
    val isOverseas: Boolean,
    val deviceChanged: Boolean,
)

data class FdsScoringResult(
    val fraudScore: BigDecimal,
    val status: FdsStatus,
    val topReasons: List<ShapReason>,
)

data class ShapReason(
    val feature: String,
    val shap: BigDecimal,
)
