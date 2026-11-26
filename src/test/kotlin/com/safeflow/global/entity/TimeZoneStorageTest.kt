package com.safeflow.global.entity

import org.junit.jupiter.api.Test
import java.util.TimeZone
import kotlin.test.assertEquals

class TimeZoneStorageTest {
    @Test
    fun `테스트 JVM 의 기본 시간대는 앱과 같은 Asia_Seoul 이다`() {
        assertEquals("Asia/Seoul", TimeZone.getDefault().id)
    }
}
