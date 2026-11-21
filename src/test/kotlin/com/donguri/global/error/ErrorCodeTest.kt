package com.donguri.global.error

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ErrorCodeTest {
    @Test
    fun `도메인 404 코드가 있어도 상태 코드 404 는 NOT_FOUND 로 매핑된다`() {
        assertTrue(ErrorCode.entries.count { it.status.value() == 404 } > 1)
        assertEquals(ErrorCode.NOT_FOUND, ErrorCode.forStatus(404))
    }

    @Test
    fun `상태 코드별 기본 코드`() {
        assertEquals(ErrorCode.UNAUTHORIZED, ErrorCode.forStatus(401))
        assertEquals(ErrorCode.ACCESS_DENIED, ErrorCode.forStatus(403))
        assertEquals(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.forStatus(405))
        assertEquals(ErrorCode.UNSUPPORTED_MEDIA_TYPE, ErrorCode.forStatus(415))
        assertEquals(ErrorCode.INVALID_INPUT, ErrorCode.forStatus(400))
        assertEquals(ErrorCode.INVALID_INPUT, ErrorCode.forStatus(409))
        assertEquals(ErrorCode.INTERNAL_ERROR, ErrorCode.forStatus(500))
        assertEquals(ErrorCode.INTERNAL_ERROR, ErrorCode.forStatus(503))
    }
}
