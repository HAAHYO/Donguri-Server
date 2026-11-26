package com.safeflow.global.util

import com.safeflow.domain.employee.entity.EmployeeRole
import com.safeflow.global.security.JwtPrincipal
import org.springframework.security.core.context.SecurityContextHolder

object MaskingUtil {
    private const val MASK = '*'
    private val ORIGINAL_VIEWERS = setOf(EmployeeRole.FDS_ADMIN)

    fun name(
        value: String,
        viewer: EmployeeRole? = currentRole(),
    ): String = if (viewer in ORIGINAL_VIEWERS) value else maskName(value)

    fun accountNumber(
        value: String,
        viewer: EmployeeRole? = currentRole(),
    ): String = if (viewer in ORIGINAL_VIEWERS) value else maskAccountNumber(value)

    fun maskName(name: String): String =
        when (name.length) {
            0, 1 -> name
            2 -> "${name.first()}$MASK"
            else -> name.first() + MASK.toString().repeat(name.length - 2) + name.last()
        }

    fun maskAccountNumber(accountNumber: String): String {
        val groups = accountNumber.split("-")
        if (groups.size >= 3) {
            val middle = groups.subList(1, groups.size - 1).map { MASK.toString().repeat(it.length) }
            return (listOf(groups.first()) + middle + groups.last()).joinToString("-")
        }
        val digitCount = accountNumber.count(Char::isDigit)
        var digitIndex = 0
        return accountNumber
            .map { c ->
                if (!c.isDigit()) {
                    c
                } else {
                    val keep = digitCount > 7 && (digitIndex < 3 || digitIndex >= digitCount - 4)
                    digitIndex++
                    if (keep) c else MASK
                }
            }.joinToString("")
    }

    fun currentRole(): EmployeeRole? = (SecurityContextHolder.getContext().authentication?.principal as? JwtPrincipal)?.role
}
