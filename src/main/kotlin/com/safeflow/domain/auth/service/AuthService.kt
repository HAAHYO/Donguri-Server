package com.safeflow.domain.auth.service

import com.safeflow.domain.auth.dto.response.LoginResponse
import com.safeflow.domain.employee.repository.EmployeeRepository
import com.safeflow.global.exception.InvalidCredentialsException
import com.safeflow.global.security.jwt.JwtTokenProvider
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val employeeRepository: EmployeeRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider,
) {
    private val dummyPasswordHash: String by lazy { passwordEncoder.encode("dummy-password-for-timing").orEmpty() }

    @Transactional(readOnly = true)
    fun login(
        username: String,
        password: String,
    ): LoginResponse {
        val employee = employeeRepository.findByUsername(username)
        if (employee == null) {
            passwordEncoder.matches(password, dummyPasswordHash)
            throw InvalidCredentialsException()
        }
        if (!passwordEncoder.matches(password, employee.password)) throw InvalidCredentialsException()
        return LoginResponse.from(employee, jwtTokenProvider.createAccessToken(employee.username, employee.role))
    }
}
