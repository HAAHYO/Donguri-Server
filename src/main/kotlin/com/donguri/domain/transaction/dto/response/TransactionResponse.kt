package com.donguri.domain.transaction.dto.response

import com.donguri.domain.transaction.entity.FdsStatus
import com.donguri.domain.transaction.entity.Transaction
import com.donguri.global.util.MaskingUtil
import java.math.BigDecimal
import java.time.LocalDateTime

data class TransactionResponse(
    val id: Long,
    val customerId: Long,
    val customerName: String,
    val accountNumber: String,
    val amount: BigDecimal,
    val txHour: Int,
    val occurredAt: LocalDateTime?,
    val isOverseas: Boolean,
    val deviceChanged: Boolean,
    val fraudScore: BigDecimal,
    val fdsStatus: FdsStatus,
    val isActualFraud: Boolean?,
    val shapReasons: List<ShapReasonResponse>,
    val churnScore: BigDecimal,
) {
    companion object {
        fun from(
            transaction: Transaction,
            shapReasons: List<ShapReasonResponse>,
        ) = TransactionResponse(
            id = requireNotNull(transaction.id),
            customerId = requireNotNull(transaction.customer.id),
            customerName = MaskingUtil.name(transaction.customer.name),
            accountNumber = MaskingUtil.accountNumber(transaction.customer.accountNumber),
            amount = transaction.amount,
            txHour = transaction.txHour,
            occurredAt = transaction.occurredAt,
            isOverseas = transaction.isOverseas,
            deviceChanged = transaction.deviceChanged,
            fraudScore = transaction.fraudScore,
            fdsStatus = transaction.fdsStatus,
            isActualFraud = transaction.isActualFraud,
            shapReasons = shapReasons,
            churnScore = transaction.customer.churnScore,
        )
    }
}
