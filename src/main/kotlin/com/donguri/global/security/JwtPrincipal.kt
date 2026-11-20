package com.donguri.global.security

import com.donguri.employee.EmployeeRole
import org.springframework.security.core.AuthenticatedPrincipal

data class JwtPrincipal(
    val username: String,
    val role: EmployeeRole,
) : AuthenticatedPrincipal {
    override fun getName(): String = username
}
