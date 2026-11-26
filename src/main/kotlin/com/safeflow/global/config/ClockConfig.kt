package com.safeflow.global.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.system(ZONE)

    companion object {
        val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
