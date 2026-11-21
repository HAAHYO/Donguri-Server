package com.donguri.domain.auth.controller

import com.donguri.domain.auth.dto.request.LoginRequest
import com.donguri.domain.auth.dto.response.LoginResponse
import com.donguri.domain.auth.service.AuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
) {
    @Operation(summary = "직원 로그인", description = "JWT 액세스 토큰과 역할, 부서를 반환한다.")
    @SecurityRequirements
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
    ): LoginResponse = authService.login(request.username, request.password)
}
