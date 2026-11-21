package com.donguri.domain.employee.repository
import com.donguri.domain.employee.entity.Employee
import org.springframework.data.jpa.repository.JpaRepository

interface EmployeeRepository : JpaRepository<Employee, Long> {
    fun findByUsername(username: String): Employee?
}
