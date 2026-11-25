package com.safeflow.domain.employee.repository

import com.safeflow.domain.employee.entity.Employee
import org.springframework.data.jpa.repository.JpaRepository

interface EmployeeRepository : JpaRepository<Employee, Long> {
    fun findByUsername(username: String): Employee?
}
