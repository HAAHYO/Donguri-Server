package com.donguri.global.error

import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail

object ProblemDetails {
    fun of(
        errorCode: ErrorCode,
        detail: String = errorCode.message,
        status: HttpStatusCode = errorCode.status,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            setProperty("code", errorCode.name)
        }
}
