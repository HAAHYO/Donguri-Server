package com.safeflow.global.exception

open class DonguriException(
    val errorCode: ErrorCode,
) : RuntimeException(errorCode.message)
