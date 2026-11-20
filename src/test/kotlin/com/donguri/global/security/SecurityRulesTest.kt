package com.donguri.global.security

import com.donguri.employee.EmployeeRole
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import testsupport.SecuredTestController
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SecuredTestController::class)
class SecurityRulesTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jwtTokenProvider: JwtTokenProvider,
    @Value("\${fds.jwt.secret}") private val secret: String,
) {
    @Test
    fun `토큰이 없거나 Bearer 형식이 아니면 401 을 반환한다`() {
        expect("/test/authenticated", null, 401, "UNAUTHORIZED")
        expect("/test/authenticated", "Basic abc", 401, "UNAUTHORIZED")
        expect("/test/authenticated", "Bearer ", 401, "UNAUTHORIZED")
    }

    @Test
    fun `위조되었거나 만료된 토큰은 401 을 반환한다`() {
        val otherKey = Base64.getEncoder().encodeToString("another-secret-key-also-32-bytes-long!!".toByteArray())
        val forged = JwtTokenProvider(otherKey, Duration.ofMinutes(30)).createAccessToken("fds_admin", EmployeeRole.FDS_ADMIN)
        val expired =
            JwtTokenProvider(secret, Duration.ofMinutes(30), Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC))
                .createAccessToken("fds_admin", EmployeeRole.FDS_ADMIN)

        expect("/test/authenticated", "Bearer $forged", 401, "UNAUTHORIZED")
        expect("/test/authenticated", "Bearer $expired", 401, "UNAUTHORIZED")
    }

    @Test
    fun `유효한 토큰이면 인증이 필요한 경로를 통과한다`() {
        expect("/test/authenticated", bearer(EmployeeRole.CRM_MARKETER), 200)
    }

    @Test
    fun `PreAuthorize 역할이 다르면 403, 같으면 200 을 반환한다`() {
        expect("/test/fds-admin", bearer(EmployeeRole.CRM_MARKETER), 403, "ACCESS_DENIED")
        expect("/test/fds-admin", bearer(EmployeeRole.FDS_ADMIN), 200)
    }

    @Test
    fun `허용 경로는 토큰 없이 접근할 수 있다`() {
        expect("/actuator/health", null, 200)
        expect("/v3/api-docs", null, 200)
    }

    @Test
    fun `OpenAPI 문서에 Bearer JWT 보안 스키마가 전역으로 등록되어 있다`() {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.components.securitySchemes.bearerAuth.scheme") { value("bearer") }
            jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat") { value("JWT") }
            jsonPath("$.security[0].bearerAuth") { exists() }
            jsonPath("$.paths['/api/auth/login'].post.security") { isEmpty() }
        }
    }

    private fun bearer(role: EmployeeRole) = "Bearer ${jwtTokenProvider.createAccessToken("u", role)}"

    private fun expect(
        path: String,
        authorization: String?,
        status: Int,
        code: String? = null,
    ) {
        mockMvc
            .get(path) {
                authorization?.let { header(HttpHeaders.AUTHORIZATION, it) }
            }.andExpect {
                status { isEqualTo(status) }
                if (code != null) jsonPath("$.code") { value(code) }
            }
    }
}
