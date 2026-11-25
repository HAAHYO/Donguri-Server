package com.safeflow.domain.auth.controller

import com.safeflow.domain.employee.entity.EmployeeRole
import com.safeflow.global.security.JwtPrincipal
import com.safeflow.global.security.jwt.JwtTokenProvider
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test", "local")
class AuthControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jwtTokenProvider: JwtTokenProvider,
) {
    private val json = JsonMapper.builder().build()

    @Test
    fun `올바른 자격으로 로그인하면 토큰과 역할, 부서를 반환한다`() {
        val body =
            mockMvc
                .post("/api/auth/login") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"username":"fds_admin","password":"test-seed-password"}"""
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.role") { value("FDS_ADMIN") }
                    jsonPath("$.department") { value("이상거래탐지팀") }
                }.andReturn()
                .response.contentAsString

        val token = json.readTree(body)["accessToken"].asString()
        assertEquals(JwtPrincipal("fds_admin", EmployeeRole.FDS_ADMIN), jwtTokenProvider.parse(token))
    }

    @Test
    fun `틀린 비밀번호와 없는 사용자는 같은 401 응답을 받는다`() {
        val wrongPassword = login("""{"username":"crm_marketer","password":"wrong"}""", 401)
        val unknownUser = login("""{"username":"nobody","password":"test-seed-password"}""", 401)

        assertEquals("INVALID_CREDENTIALS", json.readTree(wrongPassword)["code"].asString())
        assertEquals(json.readTree(wrongPassword)["detail"], json.readTree(unknownUser)["detail"])
        assertEquals(json.readTree(wrongPassword)["code"], json.readTree(unknownUser)["code"])
    }

    @Test
    fun `username 이나 password 가 비어 있거나 없으면 400 을 반환한다`() {
        listOf(
            """{"username":"","password":"test-seed-password"}""",
            """{"username":"fds_admin","password":"  "}""",
            """{"username":"fds_admin"}""",
        ).forEach {
            assertEquals("INVALID_INPUT", json.readTree(login(it, 400))["code"].asString(), it)
        }
    }

    @Test
    fun `본문이 JSON 이 아니면 400 을 반환한다`() {
        assertEquals("INVALID_INPUT", json.readTree(login("not-json", 400))["code"].asString())
    }

    private fun login(
        content: String,
        expectedStatus: Int,
    ): String =
        mockMvc
            .post("/api/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                this.content = content
            }.andExpect { status { isEqualTo(expectedStatus) } }
            .andReturn()
            .response.contentAsString
}
