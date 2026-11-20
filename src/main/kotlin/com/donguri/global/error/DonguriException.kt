package com.donguri.global.error

open class DonguriException(
    val errorCode: ErrorCode,
) : RuntimeException(errorCode.message)
