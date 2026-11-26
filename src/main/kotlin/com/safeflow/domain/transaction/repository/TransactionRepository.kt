package com.safeflow.domain.transaction.repository

import com.safeflow.domain.transaction.entity.Transaction
import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints

interface TransactionRepository : JpaRepository<Transaction, Long> {
    @Query("select t from Transaction t join fetch t.customer order by t.createdAt desc, t.id desc")
    fun findAllWithCustomer(): List<Transaction>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select t from Transaction t where t.id = :id")
    fun findByIdForUpdate(id: Long): Transaction?
}
