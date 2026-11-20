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
    @Column(nullable = false, precision = 19, scale = 2)
    var avgTxAmount: BigDecimal,
    @Column(nullable = false)
    var appLoginFreq: Int,
    @Column(nullable = false, precision = 5, scale = 4)
    var churnScore: BigDecimal,
    @Column(nullable = false)
    var isFalsePositiveVictim: Boolean = false,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
