package com.donguri.domain.customer.repository
import com.donguri.domain.customer.entity.Customer
import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints

interface CustomerRepository : JpaRepository<Customer, Long> {
    fun findByAccountNumber(accountNumber: String): Customer?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select c from Customer c where c.id = :id")
    fun findByIdForUpdate(id: Long): Customer?
}
