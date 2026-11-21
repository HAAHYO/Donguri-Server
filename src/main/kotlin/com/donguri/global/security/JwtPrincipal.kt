package com.donguri.global.security

import com.donguri.domain.employee.entity.EmployeeRole
import org.springframework.security.core.AuthenticatedPrincipal

data class JwtPrincipal(
    val username: String,
    val role: EmployeeRole,
) : AuthenticatedPrincipal {
    override fun getName(): String = username
}
