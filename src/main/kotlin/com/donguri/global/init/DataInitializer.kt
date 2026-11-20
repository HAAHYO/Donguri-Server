package com.donguri.global.init

import com.donguri.customer.Customer
import com.donguri.customer.CustomerGrade
import com.donguri.customer.CustomerRepository
import com.donguri.employee.Employee
import com.donguri.employee.EmployeeRepository
import com.donguri.employee.EmployeeRole
import com.donguri.transaction.FdsStatus
import com.donguri.transaction.Transaction
import com.donguri.transaction.TransactionRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Component
@Profile("local")
class DataInitializer(
    private val employeeRepository: EmployeeRepository,
    private val customerRepository: CustomerRepository,
    private val transactionRepository: TransactionRepository,
    @Value("\${fds.seed.employee-password:}") private val seedPassword: String,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)
    private val passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    @Transactional
    override fun run(args: ApplicationArguments) {
        seedEmployees()
        seedCustomersAndTransactions()
    }

    private fun seedEmployees() {
        if (employeeRepository.count() > 0) return

        val rawPassword =
            seedPassword.ifBlank {
                log.warn("FDS_SEED_PASSWORD 가 설정되지 않아 직원 계정을 임의 비밀번호로 생성합니다(로그인 불가).")
                UUID.randomUUID().toString()
            }
        val encoded = requireNotNull(passwordEncoder.encode(rawPassword))
        employeeRepository.saveAll(
            listOf(
                Employee("fds_admin", encoded, EmployeeRole.FDS_ADMIN, "이상거래탐지팀"),
                Employee("crm_marketer", encoded, EmployeeRole.CRM_MARKETER, "고객관리팀"),
            ),
        )
        log.info("직원 2명을 시딩했습니다.")
    }

    private fun seedCustomersAndTransactions() {
        if (customerRepository.count() > 0 || transactionRepository.count() > 0) return

        val customers = customerRepository.saveAll(seedCustomers())
        transactionRepository.saveAll(seedTransactions(customers))
        log.info("고객 {}명, 거래 {}건을 시딩했습니다.", customers.size, transactionRepository.count())
    }

    private fun seedCustomers(): List<Customer> =
        listOf(
            customer("김민준", "110-234-567801", "152300000", CustomerGrade.VIP, "3200000", 42, "0.1200"),
            customer("이서연", "110-345-678902", "98750000", CustomerGrade.VIP, "2450000", 8, "0.7800", falsePositiveVictim = true),
            customer("박지훈", "110-456-789003", "45200000", CustomerGrade.GOLD, "850000", 25, "0.2100"),
            customer("최수아", "110-567-890104", "31800000", CustomerGrade.GOLD, "620000", 4, "0.6600", falsePositiveVictim = true),
            customer("정도윤", "110-678-901205", "27450000", CustomerGrade.GOLD, "540000", 18, "0.1800"),
            customer("강하은", "110-789-012306", "5320000", CustomerGrade.GENERAL, "120000", 30, "0.0900"),
            customer("조예준", "110-890-123407", "2180000", CustomerGrade.GENERAL, "85000", 12, "0.3500"),
            customer("윤지우", "110-901-234508", "8940000", CustomerGrade.GENERAL, "210000", 3, "0.7100", falsePositiveVictim = true),
            customer("장시우", "110-012-345609", "1250000", CustomerGrade.GENERAL, "45000", 22, "0.1500"),
            customer("임채원", "110-123-456710", "210400000", CustomerGrade.VIP, "5100000", 35, "0.0800"),
        )

    private fun seedTransactions(c: List<Customer>): List<Transaction> =
        listOf(
            tx(c[0], "2800000", 14, false, false, "0.0820", FdsStatus.NORMAL, false, shap("amount_ratio" to 0.031, "tx_hour" to 0.012)),
            tx(
                c[0],
                "15000000",
                3,
                true,
                true,
                "0.9130",
                FdsStatus.BLOCKED,
                true,
                shap("device_changed" to 0.352, "is_overseas" to 0.284, "amount_ratio" to 0.197, "tx_hour" to 0.081),
            ),
            tx(
                c[1],
                "4500000",
                23,
                true,
                false,
                "0.8420",
                FdsStatus.BLOCKED,
                false,
                shap("is_overseas" to 0.401, "tx_hour" to 0.219, "amount_ratio" to 0.122),
            ),
            tx(c[1], "1980000", 21, true, false, "0.6150", FdsStatus.WARNING, false, shap("is_overseas" to 0.388, "tx_hour" to 0.104)),
            tx(c[2], "900000", 10, false, false, "0.0510", FdsStatus.NORMAL, false, shap("amount_ratio" to 0.018)),
            tx(
                c[2],
                "3000000",
                2,
                false,
                true,
                "0.5830",
                FdsStatus.WARNING,
                null,
                shap("device_changed" to 0.274, "tx_hour" to 0.188, "amount_ratio" to 0.096),
            ),
            tx(
                c[3],
                "2400000",
                1,
                false,
                true,
                "0.8710",
                FdsStatus.BLOCKED,
                false,
                shap("device_changed" to 0.362, "tx_hour" to 0.297, "amount_ratio" to 0.187),
            ),
            tx(c[4], "450000", 12, false, false, "0.0340", FdsStatus.NORMAL, false, shap("amount_ratio" to 0.011)),
            tx(c[5], "150000", 19, false, false, "0.0280", FdsStatus.NORMAL, false, shap("amount_ratio" to 0.009)),
            tx(
                c[6],
                "4990000",
                4,
                true,
                true,
                "0.9560",
                FdsStatus.BLOCKED,
                true,
                shap("amount_ratio" to 0.341, "device_changed" to 0.301, "is_overseas" to 0.227, "tx_hour" to 0.071),
            ),
            tx(
                c[7],
                "1200000",
                22,
                false,
                true,
                "0.8050",
                FdsStatus.BLOCKED,
                false,
                shap("amount_ratio" to 0.336, "device_changed" to 0.284, "tx_hour" to 0.125),
            ),
            tx(c[8], "38000", 8, false, false, "0.0150", FdsStatus.NORMAL, false, shap("tx_hour" to 0.006)),
            tx(c[9], "6200000", 15, false, false, "0.1040", FdsStatus.NORMAL, false, shap("amount_ratio" to 0.052)),
            tx(c[9], "9800000", 0, true, false, "0.5270", FdsStatus.WARNING, null, shap("is_overseas" to 0.297, "tx_hour" to 0.162)),
            tx(
                c[5],
                "2000000",
                3,
                false,
                true,
                "0.7420",
                FdsStatus.WARNING,
                null,
                shap("amount_ratio" to 0.318, "device_changed" to 0.244, "tx_hour" to 0.143),
            ),
        )

    private fun customer(
        name: String,
        accountNumber: String,
        balance: String,
        grade: CustomerGrade,
        avgTxAmount: String,
        appLoginFreq: Int,
        churnScore: String,
        falsePositiveVictim: Boolean = false,
    ) = Customer(
        name = name,
        accountNumber = accountNumber,
        balance = BigDecimal(balance),
        grade = grade,
        avgTxAmount = BigDecimal(avgTxAmount),
        appLoginFreq = appLoginFreq,
        churnScore = BigDecimal(churnScore),
        isFalsePositiveVictim = falsePositiveVictim,
    )

    private fun tx(
        customer: Customer,
        amount: String,
        txHour: Int,
        isOverseas: Boolean,
        deviceChanged: Boolean,
        fraudScore: String,
        fdsStatus: FdsStatus,
        isActualFraud: Boolean?,
        shapReasons: String,
    ) = Transaction(
        customer = customer,
        amount = BigDecimal(amount),
        txHour = txHour,
        isOverseas = isOverseas,
        deviceChanged = deviceChanged,
        fraudScore = BigDecimal(fraudScore),
        fdsStatus = fdsStatus,
        isActualFraud = isActualFraud,
        shapReasons = shapReasons,
    )

    private fun shap(vararg contributions: Pair<String, Double>): String =
        contributions.joinToString(prefix = "[", postfix = "]") { (feature, value) ->
            """{"feature":"$feature","shap":$value}"""
        }
}
