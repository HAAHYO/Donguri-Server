package com.safeflow.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId
import java.util.TimeZone

@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock {
        val jvmZone = TimeZone.getDefault().toZoneId()
        check(jvmZone == ZONE) { "JVM 기본 시간대가 ${ZONE.id} 가 아니라 ${jvmZone.id} 입니다. 시각이 DB 에 어긋나게 저장됩니다." }
        return Clock.system(ZONE)
    }

    companion object {
        val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
