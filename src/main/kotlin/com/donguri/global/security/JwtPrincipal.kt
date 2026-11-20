package com.donguri.global.security

import com.donguri.employee.EmployeeRole

data class JwtPrincipal(
    val username: String,
    val role: EmployeeRole,
)
