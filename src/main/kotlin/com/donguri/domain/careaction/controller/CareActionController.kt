package com.donguri.domain.careaction.controller

import com.donguri.domain.careaction.dto.request.SendCareActionRequest
import com.donguri.domain.careaction.dto.response.CareActionListResponse
import com.donguri.domain.careaction.dto.response.CareActionResponse
import com.donguri.domain.careaction.service.CareActionService
import com.donguri.global.security.JwtPrincipal
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/care-actions")
class CareActionController(
    private val careActionService: CareActionService,
) {
    @Operation(summary = "케어 조치 조회", description = "최신순. 고객 이름·계좌번호는 FDS_ADMIN 외에는 마스킹")
    @PreAuthorize("hasAnyRole('FDS_ADMIN', 'CRM_MARKETER')")
    @GetMapping
    fun findAll(): CareActionListResponse = careActionService.findAll()

    @Operation(summary = "케어 조치 발송 처리", description = "조회한 version 이 최신일 때만 SENT 로 바꾸고 발송자를 기록한다. 실제 메시지는 보내지 않는다")
    @PreAuthorize("hasRole('CRM_MARKETER')")
    @PatchMapping("/{id}/send")
    fun send(
        @PathVariable id: Long,
        @Valid @RequestBody request: SendCareActionRequest,
        @Parameter(hidden = true) @AuthenticationPrincipal principal: JwtPrincipal,
    ): CareActionResponse = careActionService.send(id, request.version, principal.username)
}
