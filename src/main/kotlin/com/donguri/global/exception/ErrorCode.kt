package com.donguri.global.exception

import org.springframework.http.HttpStatus

enum class ErrorCode(
    val status: HttpStatus,
    val message: String,
) {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type 입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
    LOCK_CONFLICT(HttpStatus.CONFLICT, "다른 요청을 처리 중입니다. 잠시 후 다시 시도해 주세요."),
    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "고객을 찾을 수 없습니다."),
    INSUFFICIENT_BALANCE(HttpStatus.CONFLICT, "잔액이 부족합니다."),
    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "거래를 찾을 수 없습니다."),
    TRANSACTION_NOT_BLOCKED(HttpStatus.CONFLICT, "차단된 거래만 오탐으로 판정할 수 있습니다."),
    TRANSACTION_ALREADY_JUDGED(HttpStatus.CONFLICT, "이미 판정이 끝난 거래입니다."),
    CARE_ACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "케어 조치를 찾을 수 없습니다."),
    CARE_ACTION_ALREADY_SENT(HttpStatus.CONFLICT, "이미 발송한 케어 조치입니다."),
    CARE_ACTION_STALE(HttpStatus.CONFLICT, "케어 조치 내용이 변경되었습니다. 다시 확인한 뒤 발송해 주세요."),
    ;

    companion object {
        fun forStatus(status: Int): ErrorCode =
            when (status) {
                401 -> UNAUTHORIZED
                403 -> ACCESS_DENIED
                404 -> NOT_FOUND
                405 -> METHOD_NOT_ALLOWED
                415 -> UNSUPPORTED_MEDIA_TYPE
                in 400..499 -> INVALID_INPUT
                else -> INTERNAL_ERROR
            }
    }
}
