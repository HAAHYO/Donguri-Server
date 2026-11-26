package com.safeflow.domain.careaction.repository

import com.safeflow.domain.careaction.entity.CareAction
import com.safeflow.domain.transaction.entity.Transaction
import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints

interface CareActionRepository : JpaRepository<CareAction, Long> {
    @Query("select c from CareAction c join fetch c.customer order by c.createdAt desc, c.id desc")
    fun findAllWithCustomer(): List<CareAction>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select c from CareAction c where c.id = :id")
    fun findByIdForUpdate(id: Long): CareAction?

    @Query("select c.id from CareAction c where c.transaction = :transaction")
    fun findIdByTransaction(transaction: Transaction): Long?
}
