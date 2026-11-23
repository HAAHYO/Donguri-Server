package com.donguri.domain.auth.dto.response

import com.donguri.domain.employee.entity.Employee
import com.donguri.domain.employee.entity.EmployeeRole

data class LoginResponse(
    val accessToken: String,
    val role: EmployeeRole,
    val department: String,
) {
    companion object {
        fun from(
            employee: Employee,
            accessToken: String,
        ) = LoginResponse(
            accessToken = accessToken,
            role = employee.role,
            department = employee.department,
        )
    }
}
