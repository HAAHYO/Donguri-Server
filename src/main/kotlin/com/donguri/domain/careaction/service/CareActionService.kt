package com.donguri.domain.careaction.service

import com.donguri.domain.careaction.dto.response.CareActionListResponse
import com.donguri.domain.careaction.dto.response.CareActionResponse
import com.donguri.domain.careaction.repository.CareActionRepository
import com.donguri.global.exception.CareActionNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CareActionService(
    private val careActionRepository: CareActionRepository,
) {
    @Transactional(readOnly = true)
    fun findAll(): CareActionListResponse = CareActionListResponse(careActionRepository.findAllWithCustomer().map(CareActionResponse::from))

    @Transactional
    fun send(
        id: Long,
        expectedVersion: Long,
        username: String,
    ): CareActionResponse {
        val careAction = careActionRepository.findByIdForUpdate(id) ?: throw CareActionNotFoundException()
        careAction.markSent(username, expectedVersion)
        careActionRepository.flush()
        return CareActionResponse.from(careAction)
    }
}
