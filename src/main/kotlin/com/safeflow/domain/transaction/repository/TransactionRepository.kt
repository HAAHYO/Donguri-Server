package com.safeflow.domain.transaction.repository

import com.safeflow.domain.transaction.entity.Transaction
import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import java.time.LocalDateTime

interface TransactionRepository : JpaRepository<Transaction, Long> {
    @Query("select t from Transaction t join fetch t.customer order by t.createdAt desc, t.id desc")
    fun findAllWithCustomer(): List<Transaction>

    @Query(
        "select new com.safeflow.domain.transaction.repository.RecentFdsCounts(" +
            "coalesce(sum(case when t.fdsStatus = com.safeflow.domain.transaction.entity.FdsStatus.WARNING then 1 else 0 end), 0), " +
            "coalesce(sum(case when t.fdsStatus = com.safeflow.domain.transaction.entity.FdsStatus.BLOCKED then 1 else 0 end), 0), " +
            "coalesce(sum(case when t.isActualFraud = false then 1 else 0 end), 0)) " +
            "from Transaction t where t.customer.id = :customerId and t.createdAt >= :since",
    )
    fun countRecentFds(
        customerId: Long,
        since: LocalDateTime,
    ): RecentFdsCounts

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select t from Transaction t where t.id = :id")
    fun findByIdForUpdate(id: Long): Transaction?
}
