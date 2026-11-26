package com.safeflow.domain.customer.service

import com.safeflow.domain.customer.entity.CustomerGrade
import java.math.BigDecimal

interface ChurnScoringEngine {
    fun score(input: ChurnScoringInput): ChurnScoringResult
}

data class ChurnScoringInput(
    val recentFalsePositiveCount: Int,
    val isFalsePositiveVictim: Boolean,
    val recentBlockedCount: Int,
    val recentWarningCount: Int,
    val cardUsageChangeRate: BigDecimal,
    val loginFreqChangeRate: BigDecimal,
    val appLoginFreq: Int,
    val grade: CustomerGrade,
)

data class ChurnScoringResult(
    val churnScore: BigDecimal,
    val topReasons: List<ChurnReason>,
)

data class ChurnReason(
    val feature: String,
    val contribution: BigDecimal,
)
