package com.safeflow.domain.crm.controller

import com.safeflow.domain.crm.dto.response.HighRiskCustomerListResponse
import com.safeflow.domain.crm.service.CrmService
import io.swagger.v3.oas.annotations.Operation
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/crm")
@PreAuthorize("hasRole('CRM_MARKETER')")
class CrmController(
    private val crmService: CrmService,
) {
    @Operation(
        summary = "이탈 고위험 고객 조회",
        description =
            "오탐 피해 고객이거나 이탈 점수 0.60 이상인 고객을 이탈 점수 내림차순으로, 대기 중인 케어 조치와 함께 반환한다. " +
                "케어 발송용으로 이름·계좌번호는 원본이다. 발송은 PATCH /api/care-actions/{id}/send",
    )
    @GetMapping("/high-risk-customers")
    fun findHighRiskCustomers(): HighRiskCustomerListResponse = crmService.findHighRiskCustomers()
}
