package com.safeflow.domain.transaction.controller

import com.safeflow.domain.transaction.dto.request.SimulateTransactionRequest
import com.safeflow.domain.transaction.dto.response.SimulateTransactionResponse
import com.safeflow.domain.transaction.dto.response.TransactionListResponse
import com.safeflow.domain.transaction.dto.response.TransactionResponse
import com.safeflow.domain.transaction.service.TransactionService
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/transactions")
class TransactionController(
    private val transactionService: TransactionService,
) {
    @Operation(summary = "실시간 거래 시뮬레이션", description = "FDS 스코어링 후 상태에 따라 잔액 차감, 이탈 점수 상승, 케어 조치 생성")
    @PostMapping("/simulate")
    @ResponseStatus(HttpStatus.CREATED)
    fun simulate(
        @Valid @RequestBody request: SimulateTransactionRequest,
    ): SimulateTransactionResponse = transactionService.simulate(request)

    @Operation(summary = "거래 내역 조회", description = "최신순. 고객 이름·계좌번호는 FDS_ADMIN 외에는 마스킹")
    @GetMapping
    fun findAll(): TransactionListResponse = transactionService.findAll()

    @Operation(summary = "오탐 판정", description = "차단된 미판정 거래를 정상 거래(오탐)로 판정하고 고객 케어 대상으로 등록")
    @PreAuthorize("hasRole('FDS_ADMIN')")
    @PatchMapping("/{txId}/resolve-false-positive")
    fun resolveFalsePositive(
        @PathVariable txId: Long,
    ): TransactionResponse = transactionService.resolveFalsePositive(txId)
}
