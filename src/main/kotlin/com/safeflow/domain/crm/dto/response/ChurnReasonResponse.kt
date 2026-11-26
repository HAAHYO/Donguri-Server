package com.safeflow.domain.crm.dto.response

import java.math.BigDecimal

data class ChurnReasonResponse(
    val feature: String,
    val contribution: BigDecimal,
)
