package com.safeflow.global.security

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.options

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsRulesTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `허용 Origin 의 preflight 는 인증 없이 200 과 CORS 헤더를 반환한다`() {
        mockMvc
            .options("/api/transactions") {
                header(HttpHeaders.ORIGIN, ALLOWED)
                header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, HttpHeaders.AUTHORIZATION)
            }.andExpect {
                status { isOk() }
                header { string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED) }
                header {
                    string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        org.hamcrest.Matchers.containsStringIgnoringCase("authorization"),
                    )
                }
            }
    }

    @Test
    fun `허용되지 않은 Origin 의 preflight 는 CORS 헤더 없이 403 을 반환한다`() {
        mockMvc
            .options("/api/transactions") {
                header(HttpHeaders.ORIGIN, "https://evil.example.com")
                header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
            }.andExpect {
                status { isForbidden() }
                header { doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN) }
            }
    }

    companion object {
        private const val ALLOWED = "https://allowed.example.com"
    }
}
