package com.donguri.domain.careaction.entity

import com.donguri.domain.careaction.service.CareRecommendation
import com.donguri.domain.customer.entity.Customer
import com.donguri.domain.transaction.entity.Transaction
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
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "care_action")
class CareAction(
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
    @Column(length = 50)
    var sentBy: String? = null,
) : BaseTimeEntity() {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    val customer: Customer = transaction.customer

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    fun replan(recommendation: CareRecommendation) {
        recommendedProduct = recommendation.product
        actionMessage = recommendation.message
        status = CareActionStatus.PENDING
        sentBy = null
    }
}
