package com.donguri.transaction.dto

import java.math.BigDecimal

data class ShapReasonResponse(
    val feature: String,
    val shap: BigDecimal,
)
