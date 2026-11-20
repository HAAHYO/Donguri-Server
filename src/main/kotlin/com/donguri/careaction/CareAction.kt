package com.donguri.careaction

import com.donguri.customer.Customer
import com.donguri.global.entity.BaseTimeEntity
import com.donguri.transaction.Transaction
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

/**
 * 오탐 등으로 이탈 위험이 생긴 고객에게 보낼 케어 조치. 거래 하나당 하나만 만든다.
 */
@Entity
@Table(name = "care_action")
class CareAction(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    val customer: Customer,
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, unique = true)
    val transaction: Transaction,
    @Column(nullable = false, length = 100)
    var recommendedProduct: String,
    @Column(nullable = false, length = 500)
    var actionMessage: String,
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    var status: CareActionStatus = CareActionStatus.PENDING,
    /** 발송한 직원의 username. 발송 전이면 null */
    @Column(length = 50)
    var sentBy: String? = null,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
