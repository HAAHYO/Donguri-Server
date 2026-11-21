package com.donguri.domain.careaction.repository
import com.donguri.domain.careaction.entity.CareAction
import com.donguri.domain.transaction.entity.Transaction
import org.springframework.data.jpa.repository.JpaRepository

interface CareActionRepository : JpaRepository<CareAction, Long> {
    fun findByTransaction(transaction: Transaction): CareAction?
}
