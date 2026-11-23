package com.donguri.domain.careaction.service

import com.donguri.domain.careaction.dto.response.CareActionListResponse
import com.donguri.domain.careaction.dto.response.CareActionResponse
import com.donguri.domain.careaction.dto.response.CareActionSendHistoryListResponse
import com.donguri.domain.careaction.dto.response.CareActionSendHistoryResponse
import com.donguri.domain.careaction.entity.CareActionSendHistory
import com.donguri.domain.careaction.repository.CareActionRepository
import com.donguri.domain.careaction.repository.CareActionSendHistoryRepository
import com.donguri.global.exception.CareActionNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

@Service
class CareActionService(
    private val careActionRepository: CareActionRepository,
    private val sendHistoryRepository: CareActionSendHistoryRepository,
    private val clock: Clock,
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
        sendHistoryRepository.save(
            CareActionSendHistory(
                careAction = careAction,
                sentBy = username,
                sentAt = LocalDateTime.now(clock),
                recommendedProduct = careAction.recommendedProduct,
                actionMessage = careAction.actionMessage,
                careActionVersion = expectedVersion,
            ),
        )
        careActionRepository.flush()
        return CareActionResponse.from(careAction)
    }

    @Transactional(readOnly = true)
    fun findSendHistory(id: Long): CareActionSendHistoryListResponse {
        if (!careActionRepository.existsById(id)) throw CareActionNotFoundException()
        return CareActionSendHistoryListResponse(
            sendHistoryRepository.findAllByCareActionIdOrderByIdDesc(id).map(CareActionSendHistoryResponse::from),
        )
    }
}
