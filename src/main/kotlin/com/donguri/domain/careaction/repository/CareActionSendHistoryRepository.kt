package com.donguri.domain.careaction.repository

import com.donguri.domain.careaction.entity.CareActionSendHistory
import org.springframework.data.repository.Repository

interface CareActionSendHistoryRepository : Repository<CareActionSendHistory, Long> {
    fun save(history: CareActionSendHistory): CareActionSendHistory

    fun findAllByCareActionIdOrderByIdDesc(careActionId: Long): List<CareActionSendHistory>
}
