package com.safeflow.domain.crm.controller

import com.safeflow.domain.careaction.entity.CareAction
import com.safeflow.domain.careaction.repository.CareActionRepository
import com.safeflow.domain.customer.entity.Customer
import com.safeflow.domain.customer.entity.CustomerGrade
import com.safeflow.domain.customer.repository.CustomerRepository
import com.safeflow.domain.employee.entity.EmployeeRole
import com.safeflow.domain.transaction.entity.FdsStatus
import com.safeflow.domain.transaction.entity.Transaction
import com.safeflow.domain.transaction.repository.TransactionRepository
import com.safeflow.global.security.jwt.JwtTokenProvider
import org.hamcrest.Matchers.contains
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CrmControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jwtTokenProvider: JwtTokenProvider,
    @Autowired private val customerRepository: CustomerRepository,
    @Autowired private val transactionRepository: TransactionRepository,
    @Autowired private val careActionRepository: CareActionRepository,
) {
    private val marketer = jwtTokenProvider.createAccessToken("crm_marketer", EmployeeRole.CRM_MARKETER)

    @Test
    fun `CRM_MARKETER 는 고위험 고객을 이탈 점수 순으로 원본 이름·계좌와 대기 중인 케어 조치와 함께 받는다`() {
        val victim = customer("이서연", "110-345-678902", "0.7800", falsePositiveVictim = true)
        val highChurn = customer("김민준", "110-234-567801", "0.9100")
        customer("박지훈", "110-456-789003", "0.2100")
        val falsePositiveTx = transaction(victim, FdsStatus.BLOCKED, isActualFraud = false)
        val pending = careActionRepository.save(CareAction(falsePositiveTx, "우대금리 파킹통장 + 수수료 면제 쿠폰", "불편을 드려 죄송합니다."))
        careActionRepository.save(
            CareAction(transaction(victim, FdsStatus.WARNING), "안심차단 서비스", "확인 중입니다.").also { it.markSent("crm_marketer", 0) },
        )

        mockMvc.get("/api/crm/high-risk-customers") { header(HttpHeaders.AUTHORIZATION, "Bearer $marketer") }.andExpect {
            status { isOk() }
            jsonPath("$.items.length()") { value(2) }
            jsonPath("$.items[0].customerId") { value(highChurn.id) }
            jsonPath("$.items[0].riskReasons") { value(contains("HIGH_CHURN_SCORE")) }
            jsonPath("$.items[0].pendingCareActions") { isEmpty() }
            jsonPath("$.items[1].customerId") { value(victim.id) }
            jsonPath("$.items[1].name") { value("이서연") }
            jsonPath("$.items[1].accountNumber") { value("110-345-678902") }
            jsonPath("$.items[1].grade") { value("GENERAL") }
            jsonPath("$.items[1].churnScore") { value(0.78) }
            jsonPath("$.items[1].isFalsePositiveVictim") { value(true) }
            jsonPath("$.items[1].riskReasons") { value(contains("FALSE_POSITIVE_VICTIM", "HIGH_CHURN_SCORE")) }
            jsonPath("$.items[1].pendingCareActions.length()") { value(1) }
            jsonPath("$.items[1].pendingCareActions[0].id") { value(pending.id) }
            jsonPath("$.items[1].pendingCareActions[0].version") { value(0) }
            jsonPath("$.items[1].pendingCareActions[0].transactionId") { value(falsePositiveTx.id) }
            jsonPath("$.items[1].pendingCareActions[0].fdsStatus") { value("BLOCKED") }
            jsonPath("$.items[1].pendingCareActions[0].isFalsePositive") { value(true) }
            jsonPath("$.items[1].pendingCareActions[0].recommendedProduct") { value("우대금리 파킹통장 + 수수료 면제 쿠폰") }
            jsonPath("$.items[1].pendingCareActions[0].actionMessage") { value("불편을 드려 죄송합니다.") }
        }
    }

    @Test
    fun `토큰이 없으면 401, CRM_MARKETER 가 아닌 FDS_ADMIN 은 403 이다`() {
        val admin = jwtTokenProvider.createAccessToken("fds_admin", EmployeeRole.FDS_ADMIN)

        mockMvc.get("/api/crm/high-risk-customers").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }
        mockMvc.get("/api/crm/high-risk-customers") { header(HttpHeaders.AUTHORIZATION, "Bearer $admin") }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("ACCESS_DENIED") }
        }
    }

    private fun customer(
        name: String,
        accountNumber: String,
        churnScore: String,
        falsePositiveVictim: Boolean = false,
    ) = customerRepository.save(
        Customer(
            name = name,
            accountNumber = accountNumber,
            balance = BigDecimal("1000000.00"),
            grade = CustomerGrade.GENERAL,
            avgTxAmount = BigDecimal("100000.00"),
            appLoginFreq = 10,
            churnScore = BigDecimal(churnScore),
            isFalsePositiveVictim = falsePositiveVictim,
        ),
    )

    private fun transaction(
        customer: Customer,
        status: FdsStatus,
        isActualFraud: Boolean? = null,
    ) = transactionRepository.save(
        Transaction(
            customer = customer,
            amount = BigDecimal("3000000.00"),
            txHour = 3,
            isOverseas = true,
            deviceChanged = true,
            fraudScore = BigDecimal("0.9000"),
            fdsStatus = status,
            isActualFraud = isActualFraud,
            shapReasons = "[]",
        ),
    )
}
