package com.safeflow.domain.auth.dto.request

import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @NotBlank
    val username: String,
    @NotBlank
    val password: String,
)
