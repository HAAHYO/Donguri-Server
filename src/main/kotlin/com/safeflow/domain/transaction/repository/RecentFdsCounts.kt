package com.safeflow.domain.transaction.repository

data class RecentFdsCounts(
    val warning: Long,
    val blocked: Long,
    val falsePositive: Long,
)
