package com.safeflow.global.exception

open class SafeFlowException(
    val errorCode: ErrorCode,
) : RuntimeException(errorCode.message)
