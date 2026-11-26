package com.safeflow.global.init

import com.safeflow.domain.employee.entity.Employee
import com.safeflow.domain.employee.entity.EmployeeRole
import com.safeflow.domain.employee.repository.EmployeeRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Profile("prod")
@EnableConfigurationProperties(BootstrapAdminProperties::class)
class AdminBootstrap(
    private val employeeRepository: EmployeeRepository,
    private val passwordEncoder: PasswordEncoder,
    private val properties: BootstrapAdminProperties,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments) {
        if (properties.username.isBlank() || properties.password.isBlank()) return
        if (employeeRepository.count() > 0) {
            log.info("직원 계정이 이미 있어 첫 관리자 계정을 만들지 않습니다. FDS_ADMIN_PASSWORD 는 지워도 됩니다.")
            return
        }
        check(properties.password.length >= MIN_PASSWORD_LENGTH) { "FDS_ADMIN_PASSWORD 는 ${MIN_PASSWORD_LENGTH}자 이상이어야 합니다." }

        val encoded = requireNotNull(passwordEncoder.encode(properties.password))
        employeeRepository.save(Employee(properties.username, encoded, EmployeeRole.FDS_ADMIN, DEPARTMENT))
        log.info("첫 관리자 계정 {} 을 만들었습니다. FDS_ADMIN_PASSWORD 는 이제 지워도 됩니다.", properties.username)
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 12
        private const val DEPARTMENT = "이상거래탐지팀"
    }
}
