package com.safeflow.domain.crm.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import com.safeflow.domain.customer.entity.Customer
import com.safeflow.domain.customer.entity.CustomerGrade
import java.math.BigDecimal

data class HighRiskCustomerResponse(
    val customerId: Long,
    val name: String,
    val accountNumber: String,
    val grade: CustomerGrade,
    val churnScore: BigDecimal,
    @get:JsonProperty("isFalsePositiveVictim")
    val isFalsePositiveVictim: Boolean,
    val riskReasons: List<HighRiskReason>,
    val pendingCareActions: List<PendingCareActionResponse>,
) {
    companion object {
        fun from(
            customer: Customer,
            riskReasons: List<HighRiskReason>,
            pendingCareActions: List<PendingCareActionResponse>,
        ) = HighRiskCustomerResponse(
            customerId = requireNotNull(customer.id),
            name = customer.name,
            accountNumber = customer.accountNumber,
            grade = customer.grade,
            churnScore = customer.churnScore,
            isFalsePositiveVictim = customer.isFalsePositiveVictim,
            riskReasons = riskReasons,
            pendingCareActions = pendingCareActions,
        )
    }
}
