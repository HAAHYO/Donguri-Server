package com.donguri.domain.careaction.entity

import com.donguri.global.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "care_action_send_history")
class CareActionSendHistory(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "care_action_id", nullable = false, updatable = false)
    val careAction: CareAction,
    @Column(nullable = false, updatable = false, length = 50)
    val sentBy: String,
    @Column(nullable = false, updatable = false)
    val sentAt: LocalDateTime,
    @Column(nullable = false, updatable = false, length = 100)
    val recommendedProduct: String,
    @Column(nullable = false, updatable = false, length = 500)
    val actionMessage: String,
    @Column(nullable = false, updatable = false)
    val careActionVersion: Long,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
