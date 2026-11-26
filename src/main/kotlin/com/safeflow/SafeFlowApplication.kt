package com.safeflow

import com.safeflow.global.config.ClockConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import java.util.TimeZone

@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
class SafeFlowApplication

fun main(args: Array<String>) {
    TimeZone.setDefault(TimeZone.getTimeZone(ClockConfig.ZONE))
    runApplication<SafeFlowApplication>(*args)
}
