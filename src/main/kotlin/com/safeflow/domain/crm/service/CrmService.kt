package com.safeflow.domain.crm.service

import com.safeflow.domain.careaction.entity.CareActionStatus
import com.safeflow.domain.careaction.repository.CareActionRepository
import com.safeflow.domain.crm.dto.response.HighRiskCustomerListResponse
import com.safeflow.domain.crm.dto.response.HighRiskCustomerResponse
import com.safeflow.domain.crm.dto.response.HighRiskReason
import com.safeflow.domain.crm.dto.response.PendingCareActionResponse
import com.safeflow.domain.customer.entity.Customer
import com.safeflow.domain.customer.repository.CustomerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class CrmService(
    private val customerRepository: CustomerRepository,
    private val careActionRepository: CareActionRepository,
) {
    @Transactional(readOnly = true)
    fun findHighRiskCustomers(): HighRiskCustomerListResponse {
        val customers = customerRepository.findHighRisk(HIGH_CHURN_THRESHOLD)
        if (customers.isEmpty()) return HighRiskCustomerListResponse(emptyList())

        val pendingByCustomer =
            careActionRepository
                .findAllWithTransactionByCustomerIdInAndStatus(customers.map { requireNotNull(it.id) }, CareActionStatus.PENDING)
                .groupBy { it.customer.id }
        return HighRiskCustomerListResponse(
            customers.map { customer ->
                HighRiskCustomerResponse.from(
                    customer,
                    riskReasonsOf(customer),
                    pendingByCustomer[customer.id].orEmpty().map(PendingCareActionResponse::from),
                )
            },
        )
    }

    private fun riskReasonsOf(customer: Customer): List<HighRiskReason> =
        buildList {
            if (customer.isFalsePositiveVictim) add(HighRiskReason.FALSE_POSITIVE_VICTIM)
            if (customer.churnScore >= HIGH_CHURN_THRESHOLD) add(HighRiskReason.HIGH_CHURN_SCORE)
        }

    companion object {
        val HIGH_CHURN_THRESHOLD = BigDecimal("0.60")
    }
}
