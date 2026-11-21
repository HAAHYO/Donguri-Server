package com.donguri.transaction.dto

import com.donguri.transaction.FdsStatus
import com.donguri.transaction.Transaction
import java.math.BigDecimal
import java.time.LocalDateTime

data class SimulateTransactionResponse(
    val transactionId: Long,
    val customerId: Long,
    val amount: BigDecimal,
    val txHour: Int,
    val occurredAt: LocalDateTime?,
    val fraudScore: BigDecimal,
    val fdsStatus: FdsStatus,
    val shapReasons: List<ShapReasonResponse>,
    val balanceAfter: BigDecimal,
    val churnScore: BigDecimal,
    val careActionCreated: Boolean,
) {
    companion object {
        fun from(
            transaction: Transaction,
            shapReasons: List<ShapReasonResponse>,
            careActionCreated: Boolean,
        ) = SimulateTransactionResponse(
            transactionId = requireNotNull(transaction.id),
            customerId = requireNotNull(transaction.customer.id),
            amount = transaction.amount,
            txHour = transaction.txHour,
            occurredAt = transaction.occurredAt,
            fraudScore = transaction.fraudScore,
            fdsStatus = transaction.fdsStatus,
            shapReasons = shapReasons,
            balanceAfter = transaction.customer.balance,
            churnScore = transaction.customer.churnScore,
            careActionCreated = careActionCreated,
        )
    }
}
