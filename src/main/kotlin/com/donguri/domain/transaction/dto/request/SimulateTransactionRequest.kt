package com.donguri.domain.transaction.dto.request

import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.OffsetDateTime

data class SimulateTransactionRequest(
    val customerId: Long,
    @Positive
    @DecimalMax("99999999999999999.99")
    @Digits(integer = 17, fraction = 2)
    val amount: BigDecimal,
    @param:JsonProperty("isOverseas")
    @get:JsonProperty("isOverseas")
    val isOverseas: Boolean = false,
    val deviceChanged: Boolean = false,
    val occurredAt: OffsetDateTime? = null,
)
