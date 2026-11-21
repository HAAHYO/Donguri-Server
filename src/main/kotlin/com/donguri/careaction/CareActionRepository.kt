package com.donguri.careaction

import com.donguri.transaction.Transaction
import org.springframework.data.jpa.repository.JpaRepository

interface CareActionRepository : JpaRepository<CareAction, Long> {
    fun findByTransaction(transaction: Transaction): CareAction?
}
