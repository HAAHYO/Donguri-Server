package com.donguri.global.util

import com.donguri.domain.employee.entity.EmployeeRole
import com.donguri.global.security.JwtPrincipal
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertEquals

class MaskingUtilTest {
    @AfterEach
    fun clear() = SecurityContextHolder.clearContext()

    @Test
    fun `이름은 첫 글자와 끝 글자만 남기고 가린다`() {
        assertEquals("김", MaskingUtil.maskName("김"))
        assertEquals("김*", MaskingUtil.maskName("김수"))
        assertEquals("김*수", MaskingUtil.maskName("김민수"))
        assertEquals("남**민", MaskingUtil.maskName("남궁지민"))
        assertEquals("", MaskingUtil.maskName(""))
    }

    @Test
    fun `하이픈 계좌번호는 첫 그룹과 마지막 그룹만 남긴다`() {
        assertEquals("110-***-567801", MaskingUtil.maskAccountNumber("110-234-567801"))
        assertEquals("1002-***-****-1234", MaskingUtil.maskAccountNumber("1002-345-6789-1234"))
    }

    @Test
    fun `하이픈이 없거나 하나뿐인 계좌번호는 앞 3자리와 뒤 4자리만 남긴다`() {
        assertEquals("110*****8012", MaskingUtil.maskAccountNumber("110234568012"))
        assertEquals("110-*****8012", MaskingUtil.maskAccountNumber("110-234568012"))
        assertEquals("*******", MaskingUtil.maskAccountNumber("1234567"))
    }

    @Test
    fun `FDS_ADMIN 은 원본을, CRM_MARKETER 와 역할 없음은 마스킹된 값을 받는다`() {
        assertEquals("김민수", MaskingUtil.name("김민수", EmployeeRole.FDS_ADMIN))
        assertEquals("110-234-567801", MaskingUtil.accountNumber("110-234-567801", EmployeeRole.FDS_ADMIN))
        assertEquals("김*수", MaskingUtil.name("김민수", EmployeeRole.CRM_MARKETER))
        assertEquals("110-***-567801", MaskingUtil.accountNumber("110-234-567801", EmployeeRole.CRM_MARKETER))
        assertEquals("김*수", MaskingUtil.name("김민수", null))
    }

    @Test
    fun `기본값으로 현재 요청자의 권한을 사용한다`() {
        assertEquals("김*수", MaskingUtil.name("김민수"))

        authenticate(EmployeeRole.FDS_ADMIN)
        assertEquals("김민수", MaskingUtil.name("김민수"))

        authenticate(EmployeeRole.CRM_MARKETER)
        assertEquals("110-***-567801", MaskingUtil.accountNumber("110-234-567801"))
    }

    private fun authenticate(role: EmployeeRole) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(JwtPrincipal("u", role), null, emptyList())
    }
}
