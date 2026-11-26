package com.safeflow.global.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.TimeZone
import kotlin.test.assertEquals

class ClockConfigTest {
    @Test
    fun `JVM 기본 시간대가 Asia_Seoul 이면 같은 시간대의 Clock 을 만든다`() {
        assertEquals(ClockConfig.ZONE, ClockConfig().clock().zone)
    }

    @Test
    fun `JVM 기본 시간대가 Asia_Seoul 이 아니면 Clock 을 만들지 않고 실패한다`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

            assertThrows<IllegalStateException> { ClockConfig().clock() }
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
