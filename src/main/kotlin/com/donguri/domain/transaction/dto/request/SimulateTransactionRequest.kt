package com.donguri.domain.transaction.dto.request

import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.LocalDateTime

data class SimulateTransactionRequest(
    val customerId: Long,
    @Positive
    @Digits(integer = 17, fraction = 2)
    val amount: BigDecimal,
    val isOverseas: Boolean = false,
    val deviceChanged: Boolean = false,
    val occurredAt: LocalDateTime? = null,
)
