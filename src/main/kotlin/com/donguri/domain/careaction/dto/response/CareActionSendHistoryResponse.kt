package com.donguri.domain.careaction.dto.response

import com.donguri.domain.careaction.entity.CareActionSendHistory
import java.time.LocalDateTime

data class CareActionSendHistoryResponse(
    val id: Long,
    val sentBy: String,
    val sentAt: LocalDateTime,
    val recommendedProduct: String,
    val actionMessage: String,
    val careActionVersion: Long,
) {
    companion object {
        fun from(history: CareActionSendHistory) =
            CareActionSendHistoryResponse(
                id = requireNotNull(history.id),
                sentBy = history.sentBy,
                sentAt = history.sentAt,
                recommendedProduct = history.recommendedProduct,
                actionMessage = history.actionMessage,
                careActionVersion = history.careActionVersion,
            )
    }
}
