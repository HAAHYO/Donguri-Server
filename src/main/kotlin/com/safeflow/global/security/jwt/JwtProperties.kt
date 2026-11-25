package com.safeflow.global.security.jwt

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("fds.jwt")
data class JwtProperties(
    val secret: String = "",
    val accessTokenValidity: Duration = Duration.ofMinutes(30),
)
