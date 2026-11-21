package com.donguri.domain.careaction.repository

import com.donguri.domain.careaction.entity.CareActionSendHistory
import org.springframework.data.jpa.repository.JpaRepository

interface CareActionSendHistoryRepository : JpaRepository<CareActionSendHistory, Long> {
    fun findAllByCareActionIdOrderByIdDesc(careActionId: Long): List<CareActionSendHistory>
}
