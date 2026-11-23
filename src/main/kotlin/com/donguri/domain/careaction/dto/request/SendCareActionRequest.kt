package com.donguri.domain.careaction.dto.request

import jakarta.validation.constraints.PositiveOrZero

data class SendCareActionRequest(
    @PositiveOrZero
    val version: Long,
)
