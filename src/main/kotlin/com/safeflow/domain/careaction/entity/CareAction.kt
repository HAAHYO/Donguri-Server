package com.safeflow.domain.careaction.entity

import com.safeflow.domain.careaction.service.CareRecommendation
import com.safeflow.domain.customer.entity.Customer
import com.safeflow.domain.transaction.entity.Transaction
import com.safeflow.global.entity.BaseTimeEntity
import com.safeflow.global.exception.CareActionAlreadySentException
import com.safeflow.global.exception.CareActionStaleException
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
import jakarta.persistence.Version
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

    @Version
    @Column(nullable = false)
    var version: Long = 0
        protected set

    fun markSent(
        username: String,
        expectedVersion: Long,
    ) {
        if (status == CareActionStatus.SENT) throw CareActionAlreadySentException()
        if (version != expectedVersion) throw CareActionStaleException()
        status = CareActionStatus.SENT
        sentBy = username
    }

    fun replan(recommendation: CareRecommendation) {
        recommendedProduct = recommendation.product
        actionMessage = recommendation.message
        status = CareActionStatus.PENDING
        sentBy = null
    }
}
