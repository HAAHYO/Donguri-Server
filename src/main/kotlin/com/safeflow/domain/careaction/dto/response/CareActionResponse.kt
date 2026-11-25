package com.safeflow.domain.careaction.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import com.safeflow.domain.careaction.entity.CareAction
import com.safeflow.domain.careaction.entity.CareActionStatus
import com.safeflow.domain.customer.entity.CustomerGrade
import com.safeflow.global.util.MaskingUtil
import java.math.BigDecimal

data class CareActionResponse(
    val id: Long,
    val transactionId: Long,
    val customerId: Long,
    val customerName: String,
    val accountNumber: String,
    val customerGrade: CustomerGrade,
    val churnScore: BigDecimal,
    @get:JsonProperty("isFalsePositiveVictim")
    val isFalsePositiveVictim: Boolean,
    val recommendedProduct: String,
    val actionMessage: String,
    val status: CareActionStatus,
    val sentBy: String?,
    val version: Long,
) {
    companion object {
        fun from(careAction: CareAction) =
            CareActionResponse(
                id = requireNotNull(careAction.id),
                transactionId = requireNotNull(careAction.transaction.id),
                customerId = requireNotNull(careAction.customer.id),
                customerName = MaskingUtil.name(careAction.customer.name),
                accountNumber = MaskingUtil.accountNumber(careAction.customer.accountNumber),
                customerGrade = careAction.customer.grade,
                churnScore = careAction.customer.churnScore,
                isFalsePositiveVictim = careAction.customer.isFalsePositiveVictim,
                recommendedProduct = careAction.recommendedProduct,
                actionMessage = careAction.actionMessage,
                status = careAction.status,
                sentBy = careAction.sentBy,
                version = careAction.version,
            )
    }
}
