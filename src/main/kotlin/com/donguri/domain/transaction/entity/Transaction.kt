package com.donguri.domain.transaction.entity

import com.donguri.domain.customer.entity.Customer
import com.donguri.global.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Lob
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.Length
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "transactions")
class Transaction(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    val customer: Customer,
    @Column(nullable = false, precision = 19, scale = 2)
    val amount: BigDecimal,
    @Column(nullable = false)
    val txHour: Int,
    @Column(nullable = false)
    val isOverseas: Boolean,
    @Column(nullable = false)
    val deviceChanged: Boolean,
    @Column(nullable = false, precision = 5, scale = 4)
    var fraudScore: BigDecimal,
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    var fdsStatus: FdsStatus,
    @Column
    var isActualFraud: Boolean? = null,
    @Lob
    @Column(nullable = false, length = Length.LONG32)
    var shapReasons: String,
    @Column
    val occurredAt: LocalDateTime? = null,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
