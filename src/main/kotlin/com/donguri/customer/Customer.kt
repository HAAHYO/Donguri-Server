package com.donguri.customer

import com.donguri.global.entity.BaseTimeEntity
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
    /** 평균 거래 금액 */
    @Column(nullable = false, precision = 19, scale = 2)
    var avgTxAmount: BigDecimal,
    /** 월간 앱 로그인 횟수 */
    @Column(nullable = false)
    var appLoginFreq: Int,
    /** 이탈 위험 점수 (0~1) */
    @Column(nullable = false, precision = 5, scale = 4)
    var churnScore: BigDecimal,
    /** FDS 오탐(정상 거래 차단)을 겪은 고객인지 */
    @Column(nullable = false)
    var isFalsePositiveVictim: Boolean = false,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
