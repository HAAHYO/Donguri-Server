package com.donguri.global.exception

import org.slf4j.LoggerFactory
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class GlobalExceptionHandler : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(DonguriException::class)
    fun handleDonguri(e: DonguriException): ResponseEntity<ProblemDetail> = respond(ProblemDetails.of(e.errorCode))

    @ExceptionHandler(PessimisticLockingFailureException::class)
    fun handleLockConflict(e: PessimisticLockingFailureException): ResponseEntity<ProblemDetail> {
        log.warn("비관적 락 획득 실패: {}", e.message)
        return respond(ProblemDetails.of(ErrorCode.LOCK_CONFLICT))
    }

    @ExceptionHandler(AccessDeniedException::class, AuthenticationException::class)
    fun passToSecurity(e: RuntimeException): Nothing = throw e

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ProblemDetail> {
        log.error("처리되지 않은 예외", e)
        return respond(ProblemDetails.of(ErrorCode.INTERNAL_ERROR))
    }

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        statusCode: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val errorCode = ErrorCode.forStatus(statusCode.value())
        val detail =
            when (ex) {
                is MethodArgumentNotValidException ->
                    ex.bindingResult.fieldErrors
                        .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
                        .ifBlank { errorCode.message }
                is HttpMessageNotReadableException -> "요청 본문을 읽을 수 없습니다."
                else -> errorCode.message
            }
        return super.handleExceptionInternal(ex, ProblemDetails.of(errorCode, detail, statusCode), headers, statusCode, request)
    }

    private fun respond(problem: ProblemDetail): ResponseEntity<ProblemDetail> = ResponseEntity.status(problem.status).body(problem)
}
