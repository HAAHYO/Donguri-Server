package com.safeflow

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration

@SpringBootApplication(exclude = [UserDetailsServiceAutoConfiguration::class])
class SafeFlowApplication

fun main(args: Array<String>) {
    runApplication<SafeFlowApplication>(*args)
}
