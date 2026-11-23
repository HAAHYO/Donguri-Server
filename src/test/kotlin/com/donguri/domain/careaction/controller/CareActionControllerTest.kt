package com.donguri.domain.careaction.controller

import com.donguri.domain.careaction.entity.CareAction
import com.donguri.domain.careaction.entity.CareActionStatus
import com.donguri.domain.careaction.repository.CareActionRepository
import com.donguri.domain.careaction.service.CareRecommendation
import com.donguri.domain.customer.entity.Customer
import com.donguri.domain.customer.entity.CustomerGrade
import com.donguri.domain.customer.repository.CustomerRepository
import com.donguri.domain.employee.entity.EmployeeRole
import com.donguri.domain.transaction.controller.FixedClockTestConfig
import com.donguri.domain.transaction.entity.FdsStatus
import com.donguri.domain.transaction.entity.Transaction
import com.donguri.domain.transaction.repository.TransactionRepository
import com.donguri.global.security.jwt.JwtTokenProvider
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FixedClockTestConfig::class)
@Transactional
class CareActionControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jwtTokenProvider: JwtTokenProvider,
    @Autowired private val customerRepository: CustomerRepository,
    @Autowired private val transactionRepository: TransactionRepository,
    @Autowired private val careActionRepository: CareActionRepository,
    @Autowired private val em: EntityManager,
) {
    private val admin = jwtTokenProvider.createAccessToken("fds_admin", EmployeeRole.FDS_ADMIN)
    private val marketer = jwtTokenProvider.createAccessToken("crm_marketer", EmployeeRole.CRM_MARKETER)

    @Test
    fun `목록은 items 로 감싸 최신순으로 전체 필드를 반환하고 FDS_ADMIN 은 원본을 받는다`() {
        val older = careAction("110-111-000001")
        val newer = careAction("110-111-000002")

        list(admin).andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(2) }
            jsonPath("$.items[0].id") { value(newer.id) }
            jsonPath("$.items[1].id") { value(older.id) }
            jsonPath("$.items[0].transactionId") { value(newer.transaction.id) }
            jsonPath("$.items[0].customerId") { value(newer.customer.id) }
            jsonPath("$.items[0].customerName") { value("김민수") }
            jsonPath("$.items[0].accountNumber") { value("110-111-000002") }
            jsonPath("$.items[0].customerGrade") { value("VIP") }
            jsonPath("$.items[0].churnScore") { value(0.7) }
            jsonPath("$.items[0].isFalsePositiveVictim") { value(true) }
            jsonPath("$.items[0].recommendedProduct") { value("우대금리 파킹통장") }
            jsonPath("$.items[0].actionMessage") { value("불편을 드려 죄송합니다.") }
            jsonPath("$.items[0].status") { value("PENDING") }
            jsonPath("$.items[0].sentBy") { value(null) }
            jsonPath("$.items[0].version") { value(0) }
        }
    }

    @Test
    fun `CRM_MARKETER 는 마스킹된 이름과 계좌를 받는다`() {
        careAction("110-111-000003")

        list(marketer).andExpect {
            status { isOk() }
            jsonPath("$.items[0].customerName") { value("김*수") }
            jsonPath("$.items[0].accountNumber") { value("110-***-000003") }
        }
    }

    @Test
    fun `토큰 없이 조회하면 401 이다`() {
        list(null).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }
    }

    @Test
    fun `CRM_MARKETER 가 발송 처리하면 SENT 와 발송자가 저장된다`() {
        val careAction = careAction("110-111-000004")

        send(careAction.id!!, marketer).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("SENT") }
            jsonPath("$.sentBy") { value("crm_marketer") }
            jsonPath("$.customerName") { value("김*수") }
            jsonPath("$.accountNumber") { value("110-***-000004") }
            jsonPath("$.version") { value(1) }
        }

        em.flush()
        em.clear()
        val stored = careActionRepository.findById(careAction.id!!).get()
        assertEquals(CareActionStatus.SENT, stored.status)
        assertEquals("crm_marketer", stored.sentBy)
    }

    @Test
    fun `이미 발송한 케어 조치는 409 이고 발송자가 바뀌지 않는다`() {
        val careAction = careAction("110-111-000005")
        send(careAction.id!!, marketer).andExpect { status { isOk() } }
        val other = jwtTokenProvider.createAccessToken("crm_other", EmployeeRole.CRM_MARKETER)

        send(careAction.id!!, other).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CARE_ACTION_ALREADY_SENT") }
        }

        em.flush()
        em.clear()
        assertEquals("crm_marketer", careActionRepository.findById(careAction.id!!).get().sentBy)
    }

    @Test
    fun `조회 후 내용이 바뀐 케어 조치는 409 이고 최신 버전으로만 발송할 수 있다`() {
        val careAction = careAction("110-111-000007")
        careAction.replan(CareRecommendation("수수료 면제", "새 안내 메시지입니다."))
        careActionRepository.saveAndFlush(careAction)

        send(careAction.id!!, marketer, version = 0).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("CARE_ACTION_STALE") }
        }
        em.flush()
        em.clear()
        val unchanged = careActionRepository.findById(careAction.id!!).get()
        assertEquals(CareActionStatus.PENDING, unchanged.status)
        assertNull(unchanged.sentBy)

        send(careAction.id!!, marketer, version = 1).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("SENT") }
            jsonPath("$.actionMessage") { value("새 안내 메시지입니다.") }
        }
    }

    @Test
    fun `발송 요청에 올바른 version 이 없으면 400 이다`() {
        val careAction = careAction("110-111-000008")

        listOf("{}", """{"version":-1}""", "not-json").forEach { content ->
            mockMvc
                .patch("/api/care-actions/${careAction.id}/send") {
                    contentType = MediaType.APPLICATION_JSON
                    this.content = content
                    header(HttpHeaders.AUTHORIZATION, "Bearer $marketer")
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.code") { value("INVALID_INPUT") }
                }
        }
    }

    @Test
    fun `발송 처리 실패 케이스`() {
        val careAction = careAction("110-111-000006")

        send(999_999, marketer).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("CARE_ACTION_NOT_FOUND") }
        }
        send(careAction.id!!, admin).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("ACCESS_DENIED") }
        }
        send(careAction.id!!, null).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }
        assertEquals(CareActionStatus.PENDING, careActionRepository.findById(careAction.id!!).get().status)
        val histories = em.createNativeQuery("select count(*) from care_action_send_history").singleResult as Number
        assertEquals(0L, histories.toLong())
    }

    @Test
    fun `발송하면 보낸 내용과 발송자, 시각이 이력에 남는다`() {
        val careAction = careAction("110-111-000009")

        send(careAction.id!!, marketer).andExpect { status { isOk() } }

        history(careAction.id!!, admin).andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(1) }
            jsonPath("$.items[0].id") { isNumber() }
            jsonPath("$.items[0].sentBy") { value("crm_marketer") }
            jsonPath("$.items[0].sentAt") { value("2026-11-21T03:30:00") }
            jsonPath("$.items[0].recommendedProduct") { value("우대금리 파킹통장") }
            jsonPath("$.items[0].actionMessage") { value("불편을 드려 죄송합니다.") }
            jsonPath("$.items[0].careActionVersion") { value(0) }
        }
    }

    @Test
    fun `재계획으로 sentBy 가 지워져도 이전 이력은 남고 재발송 이력이 최신순으로 쌓인다`() {
        val careAction = careAction("110-111-000010")
        send(careAction.id!!, marketer, version = 0).andExpect { status { isOk() } }
        careAction.replan(CareRecommendation("수수료 면제", "새 안내 메시지입니다."))
        careActionRepository.saveAndFlush(careAction)
        assertNull(careAction.sentBy)
        val other = jwtTokenProvider.createAccessToken("crm_other", EmployeeRole.CRM_MARKETER)

        send(careAction.id!!, other, version = 2).andExpect { status { isOk() } }

        em.flush()
        em.clear()
        history(careAction.id!!, marketer).andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(2) }
            jsonPath("$.items[0].sentBy") { value("crm_other") }
            jsonPath("$.items[0].recommendedProduct") { value("수수료 면제") }
            jsonPath("$.items[0].actionMessage") { value("새 안내 메시지입니다.") }
            jsonPath("$.items[0].careActionVersion") { value(2) }
            jsonPath("$.items[1].sentBy") { value("crm_marketer") }
            jsonPath("$.items[1].recommendedProduct") { value("우대금리 파킹통장") }
            jsonPath("$.items[1].actionMessage") { value("불편을 드려 죄송합니다.") }
            jsonPath("$.items[1].careActionVersion") { value(0) }
        }
    }

    @Test
    fun `실패한 발송은 이력을 남기지 않는다`() {
        val stale = careAction("110-111-000011")
        stale.replan(CareRecommendation("수수료 면제", "새 안내 메시지입니다."))
        careActionRepository.saveAndFlush(stale)
        send(stale.id!!, marketer, version = 0).andExpect { status { isConflict() } }
        history(stale.id!!, marketer).andExpect { jsonPath("$.items.length()") { value(0) } }

        val sent = careAction("110-111-000012")
        send(sent.id!!, marketer).andExpect { status { isOk() } }
        send(sent.id!!, marketer).andExpect { status { isConflict() } }
        history(sent.id!!, marketer).andExpect { jsonPath("$.items.length()") { value(1) } }
    }

    @Test
    fun `발송 이력 조회 실패 케이스와 빈 이력`() {
        val careAction = careAction("110-111-000013")

        history(careAction.id!!, admin).andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(0) }
        }
        history(999_999, marketer).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("CARE_ACTION_NOT_FOUND") }
        }
        history(careAction.id!!, null).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }
    }

    @Test
    fun `OpenAPI 문서는 실제 JSON 키를 쓰고 인증 주체를 파라미터로 노출하지 않는다`() {
        mockMvc.get("/v3/api-docs").andExpect {
            status { isOk() }
            jsonPath("$.components.schemas.CareActionResponse.properties.isFalsePositiveVictim") { exists() }
            jsonPath("$.components.schemas.CareActionResponse.properties.falsePositiveVictim") { doesNotExist() }
            jsonPath("$.paths['/api/care-actions/{id}/send'].patch.parameters.length()") { value(1) }
            jsonPath("$.paths['/api/care-actions/{id}/send'].patch.parameters[0].name") { value("id") }
        }
    }

    private fun careAction(accountNumber: String): CareAction {
        val customer =
            customerRepository.save(
                Customer(
                    name = "김민수",
                    accountNumber = accountNumber,
                    balance = BigDecimal("1000000.00"),
                    grade = CustomerGrade.VIP,
                    avgTxAmount = BigDecimal("100000.00"),
                    appLoginFreq = 10,
                    churnScore = BigDecimal("0.7000"),
                    isFalsePositiveVictim = true,
                ),
            )
        val transaction =
            transactionRepository.save(
                Transaction(
                    customer = customer,
                    amount = BigDecimal("900000.00"),
                    txHour = 3,
                    isOverseas = true,
                    deviceChanged = true,
                    fraudScore = BigDecimal("0.9000"),
                    fdsStatus = FdsStatus.BLOCKED,
                    shapReasons = "[]",
                ),
            )
        return careActionRepository.saveAndFlush(CareAction(transaction, "우대금리 파킹통장", "불편을 드려 죄송합니다."))
    }

    private fun list(token: String?): ResultActionsDsl =
        mockMvc.get("/api/care-actions") {
            token?.let { header(HttpHeaders.AUTHORIZATION, "Bearer $it") }
        }

    private fun history(
        id: Long,
        token: String?,
    ): ResultActionsDsl =
        mockMvc.get("/api/care-actions/$id/send-history") {
            token?.let { header(HttpHeaders.AUTHORIZATION, "Bearer $it") }
        }

    private fun send(
        id: Long,
        token: String?,
        version: Long = 0,
    ): ResultActionsDsl =
        mockMvc.patch("/api/care-actions/$id/send") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"version":$version}"""
            token?.let { header(HttpHeaders.AUTHORIZATION, "Bearer $it") }
        }
}
