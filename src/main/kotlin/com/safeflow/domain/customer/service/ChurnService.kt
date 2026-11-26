package com.safeflow.domain.customer.service

import com.safeflow.domain.customer.entity.Customer
import com.safeflow.domain.transaction.repository.TransactionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.LocalDateTime

@Service
class ChurnService(
    private val transactionRepository: TransactionRepository,
    private val churnScoringEngine: ChurnScoringEngine,
    private val jsonMapper: JsonMapper,
    private val clock: Clock,
) {
    @Transactional(propagation = Propagation.MANDATORY)
    fun recalculate(customer: Customer) {
        val counts = transactionRepository.countRecentFds(requireNotNull(customer.id), LocalDateTime.now(clock).minusDays(RECENT_DAYS))
        val result =
            churnScoringEngine.score(
                ChurnScoringInput(
                    recentFalsePositiveCount = counts.falsePositive.toInt(),
                    isFalsePositiveVictim = customer.isFalsePositiveVictim,
                    recentBlockedCount = counts.blocked.toInt(),
                    recentWarningCount = counts.warning.toInt(),
                    cardUsageChangeRate = customer.cardUsageChangeRate,
                    loginFreqChangeRate = customer.loginFreqChangeRate,
                    appLoginFreq = customer.appLoginFreq,
                    grade = customer.grade,
                ),
            )
        customer.updateChurn(result.churnScore, jsonMapper.writeValueAsString(result.topReasons))
    }

    companion object {
        private const val RECENT_DAYS = 30L
    }
}
