package com.safeflow.global.init

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:prod_bootstrap_admin;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "fds.jwt.secret=dGVzdC1vbmx5LWp3dC1zZWNyZXQta2V5LWZvci1kb25ndXJpLWZkcy0zMmJ5dGVz",
        "fds.bootstrap-admin.username=first_admin",
        "fds.bootstrap-admin.password=first-admin-pass-123",
    ],
)
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class AdminBootstrapProdProfileTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @Test
    fun `prod 프로필로 빈 DB 에서 기동하면 설정한 첫 관리자로 로그인할 수 있다`() {
        mockMvc
            .post("/api/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"username":"first_admin","password":"first-admin-pass-123"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.role") { value("FDS_ADMIN") }
            }
    }
}
