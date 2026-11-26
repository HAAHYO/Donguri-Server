package com.safeflow.domain.transaction.entity

import java.math.BigDecimal

enum class FdsStatus {
    NORMAL,
    WARNING,
    BLOCKED,
    ;

    companion object {
        private val BLOCKED_THRESHOLD = BigDecimal("0.80")
        private val WARNING_THRESHOLD = BigDecimal("0.50")

        fun of(fraudScore: BigDecimal): FdsStatus =
            when {
                fraudScore >= BLOCKED_THRESHOLD -> BLOCKED
                fraudScore >= WARNING_THRESHOLD -> WARNING
                else -> NORMAL
            }
    }
}
