package com.donguri.transaction

import com.donguri.careaction.CareAction
import com.donguri.careaction.CareActionRepository
import com.donguri.careaction.CareRecommendation
import com.donguri.customer.CustomerNotFoundException
import com.donguri.customer.CustomerRepository
import com.donguri.transaction.dto.ShapReasonResponse
import com.donguri.transaction.dto.SimulateTransactionRequest
import com.donguri.transaction.dto.SimulateTransactionResponse
import com.donguri.transaction.dto.TransactionListResponse
import com.donguri.transaction.dto.TransactionResponse
import com.donguri.transaction.scoring.FdsScoringEngine
import com.donguri.transaction.scoring.FdsScoringInput
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDateTime

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
        val occurredAt = request.occurredAt ?: LocalDateTime.now(clock)
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
                    shapReasons = jsonMapper.writeValueAsString(reasons),
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
        val careAction = careActionRepository.findByTransaction(transaction)
        if (careAction == null) {
            careActionRepository.save(CareAction(transaction, recommendation.product, recommendation.message))
        } else {
            careAction.replan(recommendation)
        }
        return TransactionResponse.from(transaction, reasonsOf(transaction))
    }

    private fun reasonsOf(transaction: Transaction): List<ShapReasonResponse> = jsonMapper.readValue(transaction.shapReasons)

    companion object {
        private val CHURN_INCREASE =
            mapOf(
                FdsStatus.WARNING to BigDecimal("0.05"),
                FdsStatus.BLOCKED to BigDecimal("0.10"),
            )
        private val FALSE_POSITIVE_CHURN_INCREASE = BigDecimal("0.30")
        private const val MONEY_SCALE = 2
    }
}
