package com.donguri.global.security.jwt

import com.donguri.domain.employee.entity.EmployeeRole
import com.donguri.global.security.JwtPrincipal
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import java.time.Clock
import java.time.Duration
import java.util.Date
import javax.crypto.SecretKey

class JwtTokenProvider(
    base64Secret: String,
    private val accessTokenValidity: Duration,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret))

    fun createAccessToken(
        username: String,
        role: EmployeeRole,
    ): String {
        val now = clock.instant()
        return Jwts
            .builder()
            .subject(username)
            .claim(USERNAME_CLAIM, username)
            .claim(ROLE_CLAIM, role.name)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(accessTokenValidity)))
            .signWith(key)
            .compact()
    }

    fun parse(token: String): JwtPrincipal? {
        val claims =
            try {
                Jwts
                    .parser()
                    .verifyWith(key)
                    .clock { Date.from(clock.instant()) }
                    .build()
                    .parseSignedClaims(token)
                    .payload
            } catch (e: JwtException) {
                return null
            } catch (e: IllegalArgumentException) {
                return null
            }
        val username = claims[USERNAME_CLAIM] as? String ?: return null
        val role = (claims[ROLE_CLAIM] as? String)?.let { name -> EmployeeRole.entries.find { it.name == name } } ?: return null
        return JwtPrincipal(username, role)
    }

    companion object {
        const val USERNAME_CLAIM = "username"
        const val ROLE_CLAIM = "role"
    }
}
