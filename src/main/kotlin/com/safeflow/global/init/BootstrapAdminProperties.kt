package com.safeflow.global.init

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("fds.bootstrap-admin")
data class BootstrapAdminProperties(
    val username: String = "",
    val password: String = "",
)
