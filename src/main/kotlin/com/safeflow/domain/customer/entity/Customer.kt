package com.safeflow.domain.customer.entity

import com.safeflow.global.entity.BaseTimeEntity
import com.safeflow.global.exception.InsufficientBalanceException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.math.RoundingMode

@Entity
@Table(name = "customer")
class Customer(
    @Column(nullable = false, length = 50)
    var name: String,
    @Column(nullable = false, unique = true, length = 20)
    val accountNumber: String,
    @Column(nullable = false, precision = 19, scale = 2)
    var balance: BigDecimal,
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    var grade: CustomerGrade,
    @Column(nullable = false, precision = 19, scale = 2)
    var avgTxAmount: BigDecimal,
    @Column(nullable = false)
    var appLoginFreq: Int,
    @Column(nullable = false, precision = 5, scale = 4)
    var churnScore: BigDecimal,
    @Column(nullable = false)
    var isFalsePositiveVictim: Boolean = false,
    @Column(nullable = false, precision = 6, scale = 4)
    var cardUsageChangeRate: BigDecimal = BigDecimal("0.0000"),
    @Column(nullable = false, precision = 6, scale = 4)
    var loginFreqChangeRate: BigDecimal = BigDecimal("0.0000"),
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    fun withdraw(amount: BigDecimal) {
        if (balance < amount) throw InsufficientBalanceException()
        balance = (balance - amount).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
    }

    fun increaseChurnScore(delta: BigDecimal) {
        churnScore = (churnScore + delta).min(BigDecimal.ONE).setScale(CHURN_SCALE, RoundingMode.HALF_UP)
    }

    fun markFalsePositiveVictim() {
        isFalsePositiveVictim = true
    }

    companion object {
        private const val CHURN_SCALE = 4
        private const val MONEY_SCALE = 2
    }
}
