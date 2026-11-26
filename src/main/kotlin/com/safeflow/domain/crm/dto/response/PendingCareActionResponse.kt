package com.safeflow.domain.crm.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import com.safeflow.domain.careaction.entity.CareAction
import com.safeflow.domain.transaction.entity.FdsStatus

data class PendingCareActionResponse(
    val id: Long,
    val version: Long,
    val transactionId: Long,
    val fdsStatus: FdsStatus,
    @get:JsonProperty("isFalsePositive")
    val isFalsePositive: Boolean,
    val recommendedProduct: String,
    val actionMessage: String,
) {
    companion object {
        fun from(careAction: CareAction) =
            PendingCareActionResponse(
                id = requireNotNull(careAction.id),
                version = careAction.version,
                transactionId = requireNotNull(careAction.transaction.id),
                fdsStatus = careAction.transaction.fdsStatus,
                isFalsePositive = careAction.transaction.isActualFraud == false,
                recommendedProduct = careAction.recommendedProduct,
                actionMessage = careAction.actionMessage,
            )
    }
}
