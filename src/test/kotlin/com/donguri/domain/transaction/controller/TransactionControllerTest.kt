package com.donguri.domain.transaction.controller
import com.donguri.domain.careaction.entity.CareAction
import com.donguri.domain.careaction.entity.CareActionStatus
import com.donguri.domain.careaction.repository.CareActionRepository
import com.donguri.domain.customer.entity.Customer
import com.donguri.domain.customer.entity.CustomerGrade
import com.donguri.domain.customer.repository.CustomerRepository
import com.donguri.domain.employee.entity.EmployeeRole
import com.donguri.domain.transaction.repository.TransactionRepository
import com.donguri.global.security.jwt.JwtTokenProvider
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNull

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TransactionControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jwtTokenProvider: JwtTokenProvider,
    @Autowired private val customerRepository: CustomerRepository,
    @Autowired private val transactionRepository: TransactionRepository,
    @Autowired private val careActionRepository: CareActionRepository,
    @Autowired private val em: EntityManager,
) {
    private val json = JsonMapper.builder().build()
    private val admin = token(EmployeeRole.FDS_ADMIN)
    private val marketer = token(EmployeeRole.CRM_MARKETER)

    @Test
    fun `NORMAL 거래는 잔액을 차감하고 이탈 점수와 케어 조치는 그대로다`() {
        val customer = customer(balance = "1000000", churn = "0.1000")

        val body = simulate(customer.id, "50000", token = marketer).andExpect { status { isCreated() } }.body()

        assertEquals("NORMAL", body["fdsStatus"].asString())
        assertNumber("950000.00", body["balanceAfter"])
        assertNumber("0.1000", body["churnScore"])
        assertEquals(false, body["careActionCreated"].asBoolean())
        assertEquals(0, careActionRepository.count())
    }

    @Test
    fun `WARNING 거래는 잔액을 차감하고 이탈 점수를 올리며 PENDING 케어 조치를 만든다`() {
        val customer = customer(balance = "1000000", churn = "0.1000")

        val body = simulate(customer.id, "100000", overseas = true, device = true).andExpect { status { isCreated() } }.body()

        assertEquals("WARNING", body["fdsStatus"].asString())
        assertNumber("900000.00", body["balanceAfter"])
        assertNumber("0.1500", body["churnScore"])
        assertCareAction(body["transactionId"].asLong(), "안심차단 서비스")
    }

    @Test
    fun `BLOCKED 거래는 잔액을 차감하지 않고 이탈 점수를 올리며 PENDING 케어 조치를 만든다`() {
        val customer = customer(balance = "1000", churn = "0.1000")

        val body =
            simulate(customer.id, "500000", device = true, occurredAt = "2026-11-21T02:30:00")
                .andExpect { status { isCreated() } }
                .body()

        assertEquals("BLOCKED", body["fdsStatus"].asString())
        assertEquals(2, body["txHour"].asInt())
        assertNumber("1000.00", body["balanceAfter"])
        assertNumber("0.2000", body["churnScore"])
        assertEquals(listOf("amount_ratio", "device_changed", "tx_hour"), features(body["shapReasons"]))
        assertCareAction(body["transactionId"].asLong(), "안심차단 서비스 + 우대금리 파킹통장")
    }

    @Test
    fun `잔액이 부족하면 409 이고 거래를 저장하지 않는다`() {
        val customer = customer(balance = "10000")

        simulate(customer.id, "50000").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("INSUFFICIENT_BALANCE") }
        }
        assertEquals(0, transactionRepository.count())
    }

    @Test
    fun `없는 고객은 404, 잘못된 금액과 본문은 400, 토큰 없으면 401 이다`() {
        val customer = customer()

        simulate(999_999, "1000").andExpect { jsonPath("$.code") { value("CUSTOMER_NOT_FOUND") } }
        listOf("0", "-100", "10.123").forEach { amount ->
            simulate(customer.id, amount).andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("INVALID_INPUT") }
            }
        }
        post("/api/transactions/simulate", """{"amount":1000}""", admin).andExpect { status { isBadRequest() } }
        simulate(customer.id, "1000", token = null).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `거래 목록은 items 로 감싸고 FDS_ADMIN 은 원본, CRM_MARKETER 는 마스킹된 이름과 계좌를 받는다`() {
        val customer = customer(name = "김민수", account = "110-234-567801")
        simulate(customer.id, "1000").andExpect { status { isCreated() } }

        val adminItem = list(admin)["items"][0]
        val marketerItem = list(marketer)["items"][0]

        assertEquals("김민수", adminItem["customerName"].asString())
        assertEquals("110-234-567801", adminItem["accountNumber"].asString())
        assertEquals("김*수", marketerItem["customerName"].asString())
        assertEquals("110-***-567801", marketerItem["accountNumber"].asString())
        assertEquals(true, adminItem["shapReasons"].isArray)
    }

    @Test
    fun `차단된 미판정 거래를 오탐으로 판정하면 고객과 기존 케어 조치가 갱신된다`() {
        val customer = customer(grade = CustomerGrade.VIP, balance = "1000", churn = "0.2000")
        val txId = blockedTransaction(customer.id)
        careActionRepository.findAll().single().status = CareActionStatus.SENT

        val body = resolve(txId, admin).andExpect { status { isOk() } }.body()

        assertEquals(false, body["isActualFraud"].asBoolean())
        assertEquals("BLOCKED", body["fdsStatus"].asString())
        assertNumber("0.6000", body["churnScore"])
        em.flush()
        em.clear()
        val updated = customerRepository.findById(customer.id!!).get()
        assertEquals(true, updated.isFalsePositiveVictim)
        assertEquals(0, BigDecimal("1000").compareTo(updated.balance))
        val careAction = careActionRepository.findAll().single()
        assertEquals("수수료 면제 + VIP 전담 상담", careAction.recommendedProduct)
        assertEquals(CareActionStatus.PENDING, careAction.status)
        assertNull(careAction.sentBy)
    }

    @Test
    fun `케어 조치가 없던 차단 거래도 오탐 판정 시 케어 조치를 만들고 이탈 점수는 1 을 넘지 않는다`() {
        val customer = customer(churn = "0.9500")
        val txId = blockedTransaction(customer.id)
        careActionRepository.deleteAll()

        val body = resolve(txId, admin).andExpect { status { isOk() } }.body()

        assertNumber("1.0000", body["churnScore"])
        assertEquals("우대금리 파킹통장 + 수수료 면제 쿠폰", careActionRepository.findAll().single().recommendedProduct)
    }

    @Test
    fun `오탐 판정 실패 케이스`() {
        val customer = customer()
        val normalTxId = simulate(customer.id, "1000").andExpect { status { isCreated() } }.body()["transactionId"].asLong()
        val blockedTxId = blockedTransaction(customer.id)

        resolve(999_999, admin).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("TRANSACTION_NOT_FOUND") }
        }
        resolve(normalTxId, admin).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("TRANSACTION_NOT_BLOCKED") }
        }
        resolve(blockedTxId, marketer).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("ACCESS_DENIED") }
        }
        resolve(blockedTxId, null).andExpect { status { isUnauthorized() } }
        resolve(blockedTxId, admin).andExpect { status { isOk() } }
        resolve(blockedTxId, admin).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("TRANSACTION_ALREADY_JUDGED") }
        }
    }

    private fun blockedTransaction(customerId: Long?): Long =
        simulate(customerId, "900000", overseas = true, device = true, occurredAt = "2026-11-21T03:00:00")
            .andExpect { jsonPath("$.fdsStatus") { value("BLOCKED") } }
            .body()["transactionId"]
            .asLong()

    private fun assertCareAction(
        transactionId: Long,
        product: String,
    ) {
        val careAction: CareAction = careActionRepository.findAll().single()
        assertEquals(transactionId, careAction.transaction.id)
        assertEquals(product, careAction.recommendedProduct)
        assertEquals(CareActionStatus.PENDING, careAction.status)
    }

    private fun customer(
        name: String = "테스트",
        account: String = "110-000-${(100000..999999).random()}",
        grade: CustomerGrade = CustomerGrade.GENERAL,
        balance: String = "1000000",
        churn: String = "0.1000",
    ): Customer =
        customerRepository.saveAndFlush(
            Customer(
                name = name,
                accountNumber = account,
                balance = BigDecimal(balance),
                grade = grade,
                avgTxAmount = BigDecimal("100000"),
                appLoginFreq = 10,
                churnScore = BigDecimal(churn),
            ),
        )

    private fun simulate(
        customerId: Long?,
        amount: String,
        overseas: Boolean = false,
        device: Boolean = false,
        occurredAt: String = "2026-11-21T14:00:00",
        token: String? = admin,
    ): ResultActionsDsl =
        post(
            "/api/transactions/simulate",
            """{"customerId":$customerId,"amount":$amount,"isOverseas":$overseas,"deviceChanged":$device,"occurredAt":"$occurredAt"}""",
            token,
        )

    private fun post(
        path: String,
        content: String,
        token: String?,
    ): ResultActionsDsl =
        mockMvc.post(path) {
            contentType = MediaType.APPLICATION_JSON
            this.content = content
            token?.let { header(HttpHeaders.AUTHORIZATION, "Bearer $it") }
        }

    private fun resolve(
        txId: Long,
        token: String?,
    ): ResultActionsDsl =
        mockMvc.patch("/api/transactions/$txId/resolve-false-positive") {
            token?.let { header(HttpHeaders.AUTHORIZATION, "Bearer $it") }
        }

    private fun list(token: String): JsonNode =
        mockMvc
            .get("/api/transactions") { header(HttpHeaders.AUTHORIZATION, "Bearer $token") }
            .andExpect { status { isOk() } }
            .body()

    private fun assertNumber(
        expected: String,
        actual: JsonNode,
    ) = assertEquals(0, BigDecimal(expected).compareTo(actual.decimalValue()), "expected $expected but was $actual")

    private fun features(reasons: JsonNode): List<String> = (0 until reasons.size()).map { reasons[it]["feature"].asString() }

    private fun ResultActionsDsl.body(): JsonNode = json.readTree(andReturn().response.contentAsString)

    private fun token(role: EmployeeRole) = jwtTokenProvider.createAccessToken(role.name.lowercase(), role)
}
