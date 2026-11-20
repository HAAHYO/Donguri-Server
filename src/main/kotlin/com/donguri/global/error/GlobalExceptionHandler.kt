package com.donguri.global.error

import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(DonguriException::class)
    fun handleDonguri(e: DonguriException): ResponseEntity<ProblemDetail> = respond(ProblemDetails.of(e.errorCode))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleInvalid(e: MethodArgumentNotValidException): ResponseEntity<ProblemDetail> {
        val detail =
            e.bindingResult.fieldErrors
                .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
                .ifBlank { ErrorCode.INVALID_INPUT.message }
        return respond(ProblemDetails.of(ErrorCode.INVALID_INPUT, detail))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(e: HttpMessageNotReadableException): ResponseEntity<ProblemDetail> =
        respond(ProblemDetails.of(ErrorCode.INVALID_INPUT, "요청 본문을 읽을 수 없습니다."))

    private fun respond(problem: ProblemDetail): ResponseEntity<ProblemDetail> = ResponseEntity.status(problem.status).body(problem)
}
