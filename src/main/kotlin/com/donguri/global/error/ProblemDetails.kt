package com.donguri.global.error

import org.springframework.http.ProblemDetail

object ProblemDetails {
    fun of(
        errorCode: ErrorCode,
        detail: String = errorCode.message,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(errorCode.status, detail).apply {
            setProperty("code", errorCode.name)
        }
}
