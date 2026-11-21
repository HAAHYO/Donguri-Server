package com.donguri.domain.careaction.dto.response

import com.donguri.domain.careaction.entity.CareAction
import com.donguri.domain.careaction.entity.CareActionStatus
import com.donguri.domain.customer.entity.CustomerGrade
import com.donguri.global.util.MaskingUtil
import com.fasterxml.jackson.annotation.JsonProperty
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
            )
    }
}
