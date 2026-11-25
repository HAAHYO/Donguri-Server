package com.safeflow.domain.transaction.dto.response

import java.math.BigDecimal

data class ShapReasonResponse(
    val feature: String,
    val shap: BigDecimal,
)
