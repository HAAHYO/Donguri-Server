package com.safeflow.global.config

import com.safeflow.global.exception.ErrorCode
import com.safeflow.global.exception.SafeFlowException
import com.safeflow.global.security.jwt.JwtAuthenticationFilter
import com.safeflow.global.security.jwt.JwtProperties
import com.safeflow.global.security.jwt.JwtTokenProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.servlet.HandlerExceptionResolver
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties::class, CorsProperties::class)
class SecurityConfig {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    @Bean
    fun jwtTokenProvider(
        properties: JwtProperties,
        environment: Environment,
    ): JwtTokenProvider {
        val secret =
            properties.secret.ifBlank {
                check(environment.acceptsProfiles(Profiles.of("local & !prod"))) { "JWT_SECRET 환경변수가 설정되지 않았습니다." }
                log.warn("JWT_SECRET 이 설정되지 않아 임의 키를 생성합니다. 재시작하면 기존 토큰은 무효가 됩니다.")
                Base64.getEncoder().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
            }
        return JwtTokenProvider(secret, properties.accessTokenValidity)
    }

    @Bean
    fun corsConfigurationSource(properties: CorsProperties): CorsConfigurationSource {
        val configuration =
            CorsConfiguration().apply {
                allowedOriginPatterns = properties.allowedOrigins.filter { it.isNotBlank() }
                allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                allowedHeaders = listOf(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE)
                maxAge = Duration.ofHours(1).seconds
            }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", configuration) }
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtTokenProvider: JwtTokenProvider,
        @Qualifier("handlerExceptionResolver") exceptionResolver: HandlerExceptionResolver,
    ): SecurityFilterChain {
        http
            .cors { }
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .logout { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it
                    .requestMatchers(HttpMethod.POST, "/api/auth/login")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/error", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }.exceptionHandling {
                it
                    .authenticationEntryPoint { request, response, _ ->
                        exceptionResolver.resolveException(request, response, null, SafeFlowException(ErrorCode.UNAUTHORIZED))
                    }.accessDeniedHandler { request, response, _ ->
                        exceptionResolver.resolveException(request, response, null, SafeFlowException(ErrorCode.ACCESS_DENIED))
                    }
            }.addFilterBefore(JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}
