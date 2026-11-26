package com.safeflow.global.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("fds.cors")
data class CorsProperties(
    val allowedOrigins: List<String> = emptyList(),
)
