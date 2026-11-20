package com.donguri.auth.dto

import com.donguri.employee.Employee
import com.donguri.employee.EmployeeRole

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
