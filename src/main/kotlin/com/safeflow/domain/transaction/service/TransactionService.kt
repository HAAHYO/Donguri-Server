package com.safeflow.domain.transaction.service

import com.safeflow.domain.careaction.entity.CareAction
import com.safeflow.domain.careaction.repository.CareActionRepository
import com.safeflow.domain.careaction.service.CareRecommendation
import com.safeflow.domain.customer.repository.CustomerRepository
import com.safeflow.domain.transaction.dto.request.SimulateTransactionRequest
import com.safeflow.domain.transaction.dto.response.ShapReasonResponse
import com.safeflow.domain.transaction.dto.response.SimulateTransactionResponse
import com.safeflow.domain.transaction.dto.response.TransactionListResponse
import com.safeflow.domain.transaction.dto.response.TransactionResponse
import com.safeflow.domain.transaction.entity.FdsStatus
import com.safeflow.domain.transaction.entity.Transaction
import com.safeflow.domain.transaction.repository.TransactionRepository
import com.safeflow.global.exception.CustomerNotFoundException
import com.safeflow.global.exception.InvalidOccurredAtException
import com.safeflow.global.exception.TransactionAlreadyJudgedException
import com.safeflow.global.exception.TransactionNotBlockedException
import com.safeflow.global.exception.TransactionNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class TransactionService(
    private val customerRepository: CustomerRepository,
    private val transactionRepository: TransactionRepository,
    private val careActionRepository: CareActionRepository,
    private val scoringEngine: FdsScoringEngine,
    private val jsonMapper: JsonMapper,
    private val clock: Clock,
) {
    @Transactional
    fun simulate(request: SimulateTransactionRequest): SimulateTransactionResponse {
        val customer = customerRepository.findByIdForUpdate(request.customerId) ?: throw CustomerNotFoundException()
        val amount = request.amount.setScale(MONEY_SCALE, RoundingMode.UNNECESSARY)
        val occurredAt = resolveOccurredAt(request.occurredAt)
        val result =
            scoringEngine.score(
                FdsScoringInput(
                    amount = amount,
                    avgTxAmount = customer.avgTxAmount,
                    txHour = occurredAt.hour,
                    isOverseas = request.isOverseas,
                    deviceChanged = request.deviceChanged,
                ),
            )

        if (result.status != FdsStatus.BLOCKED) customer.withdraw(amount)
        CHURN_INCREASE[result.status]?.let(customer::increaseChurnScore)

        val reasons = result.topReasons.map { ShapReasonResponse(it.feature, it.shap) }
        val transaction =
            transactionRepository.save(
                Transaction(
                    customer = customer,
                    amount = amount,
                    txHour = occurredAt.hour,
                    isOverseas = request.isOverseas,
                    deviceChanged = request.deviceChanged,
                    fraudScore = result.fraudScore,
                    fdsStatus = result.status,
                    shapReasons = jsonMapper.writeValueAsString(result.topReasons.map { StoredShapReason(it.feature, it.shap) }),
                    occurredAt = occurredAt,
                ),
            )
        val careAction =
            CareRecommendation.forDetection(result.status)?.let {
                careActionRepository.save(CareAction(transaction, it.product, it.message))
            }
        return SimulateTransactionResponse.from(transaction, reasons, careActionCreated = careAction != null)
    }

    @Transactional(readOnly = true)
    fun findAll(): TransactionListResponse =
        TransactionListResponse(transactionRepository.findAllWithCustomer().map { TransactionResponse.from(it, reasonsOf(it)) })

    @Transactional
    fun resolveFalsePositive(transactionId: Long): TransactionResponse {
        val transaction = transactionRepository.findByIdForUpdate(transactionId) ?: throw TransactionNotFoundException()
        if (transaction.fdsStatus != FdsStatus.BLOCKED) throw TransactionNotBlockedException()
        if (transaction.isActualFraud != null) throw TransactionAlreadyJudgedException()
        val customer =
            customerRepository.findByIdForUpdate(requireNotNull(transaction.customer.id)) ?: throw CustomerNotFoundException()

        transaction.isActualFraud = false
        customer.markFalsePositiveVictim()
        customer.increaseChurnScore(FALSE_POSITIVE_CHURN_INCREASE)

        val recommendation = CareRecommendation.forFalsePositive(customer)
        val careAction = careActionRepository.findIdByTransaction(transaction)?.let(careActionRepository::findByIdForUpdate)
        if (careAction == null) {
            careActionRepository.save(CareAction(transaction, recommendation.product, recommendation.message))
        } else {
            careAction.replan(recommendation)
        }
        return TransactionResponse.from(transaction, reasonsOf(transaction))
    }

    private fun resolveOccurredAt(value: OffsetDateTime?): LocalDateTime {
        if (value == null) return LocalDateTime.now(clock)
        if (value.isBefore(MIN_OCCURRED_AT) || !value.isBefore(MAX_OCCURRED_AT)) throw InvalidOccurredAtException()
        return value.atZoneSameInstant(clock.zone).toLocalDateTime()
    }

    private fun reasonsOf(transaction: Transaction): List<ShapReasonResponse> =
        jsonMapper
            .readValue<List<StoredShapReason>>(transaction.shapReasons)
            .map { ShapReasonResponse(it.feature, it.shap) }

    companion object {
        private val CHURN_INCREASE =
            mapOf(
                FdsStatus.WARNING to BigDecimal("0.05"),
                FdsStatus.BLOCKED to BigDecimal("0.10"),
            )
        private val FALSE_POSITIVE_CHURN_INCREASE = BigDecimal("0.30")
        private const val MONEY_SCALE = 2
        private val MIN_OCCURRED_AT = OffsetDateTime.of(2000, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        private val MAX_OCCURRED_AT = OffsetDateTime.of(2100, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
    }
}

internal data class StoredShapReason(
    val feature: String,
    val shap: BigDecimal,
)
