package com.safeflow.global.security

import com.safeflow.domain.employee.entity.EmployeeRole
import org.springframework.security.core.AuthenticatedPrincipal

data class JwtPrincipal(
    val username: String,
    val role: EmployeeRole,
) : AuthenticatedPrincipal {
    override fun getName(): String = username
}
