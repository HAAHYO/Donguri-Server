package com.donguri.global.security

import com.donguri.employee.EmployeeRole
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.WeakKeyException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.env.MockEnvironment
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JwtTokenProviderTest {
    private val secret = Base64.getEncoder().encodeToString("unit-test-secret-key-must-be-32-bytes!!".toByteArray())
    private val otherSecret = Base64.getEncoder().encodeToString("another-secret-key-also-32-bytes-long!!".toByteArray())
    private val now = Instant.parse("2026-11-20T00:00:00Z")
    private val provider = JwtTokenProvider(secret, Duration.ofMinutes(30), Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `발급한 토큰을 파싱하면 username 과 role 이 그대로 나온다`() {
        val token = provider.createAccessToken("fds_admin", EmployeeRole.FDS_ADMIN)

        assertEquals(JwtPrincipal("fds_admin", EmployeeRole.FDS_ADMIN), provider.parse(token))
    }

    @Test
    fun `토큰 claim 에 username 과 role 문자열이 들어 있다`() {
        val token = provider.createAccessToken("crm_marketer", EmployeeRole.CRM_MARKETER)
        val claims =
            Jwts
                .parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)))
                .clock { java.util.Date.from(now) }
                .build()
                .parseSignedClaims(token)
                .payload

        assertEquals("crm_marketer", claims["username"])
        assertEquals("CRM_MARKETER", claims["role"])
    }

    @Test
    fun `다른 키로 서명된 토큰은 거부한다`() {
        val forged = JwtTokenProvider(otherSecret, Duration.ofMinutes(30), Clock.fixed(now, ZoneOffset.UTC))

        assertNull(provider.parse(forged.createAccessToken("fds_admin", EmployeeRole.FDS_ADMIN)))
    }

    @Test
    fun `만료된 토큰은 거부한다`() {
        val token = provider.createAccessToken("fds_admin", EmployeeRole.FDS_ADMIN)
        val later = JwtTokenProvider(secret, Duration.ofMinutes(30), Clock.fixed(now.plus(Duration.ofMinutes(31)), ZoneOffset.UTC))

        assertNull(later.parse(token))
    }

    @Test
    fun `형식이 깨진 토큰과 role 이 없거나 모르는 값인 토큰은 거부한다`() {
        val key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))
        val noRole =
            Jwts
                .builder()
                .claim("username", "x")
                .signWith(key)
                .compact()
        val unknownRole =
            Jwts
                .builder()
                .claim("username", "x")
                .claim("role", "SUPER_ADMIN")
                .signWith(key)
                .compact()

        assertNull(provider.parse("not-a-jwt"))
        assertNull(provider.parse(noRole))
        assertNull(provider.parse(unknownRole))
    }

    @Test
    fun `256비트보다 짧은 키는 생성 시점에 거부한다`() {
        val shortKey = Base64.getEncoder().encodeToString("too-short".toByteArray())

        assertThrows<WeakKeyException> { JwtTokenProvider(shortKey, Duration.ofMinutes(30)) }
    }

    @Test
    fun `JWT_SECRET 이 없으면 local 프로필에서만 임의 키로 기동하고 그 외에는 실패한다`() {
        val config = SecurityConfig()

        assertNotNull(config.jwtTokenProvider(JwtProperties(secret = ""), MockEnvironment().apply { setActiveProfiles("local") }))
        assertThrows<IllegalStateException> { config.jwtTokenProvider(JwtProperties(secret = ""), MockEnvironment()) }
        assertThrows<IllegalStateException> {
            config.jwtTokenProvider(JwtProperties(secret = ""), MockEnvironment().apply { setActiveProfiles("prod") })
        }
    }
}
