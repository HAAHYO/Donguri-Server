package com.donguri.transaction

import com.donguri.customer.Customer
import com.donguri.global.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Lob
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal

// TRANSACTION 은 SQL 키워드라 테이블명을 복수형으로 둔다
@Entity
@Table(name = "transactions")
class Transaction(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    val customer: Customer,
    @Column(nullable = false, precision = 19, scale = 2)
    val amount: BigDecimal,
    /** 거래 시각(0~23시) */
    @Column(nullable = false)
    val txHour: Int,
    @Column(nullable = false)
    val isOverseas: Boolean,
    /** 평소와 다른 기기에서 발생했는지 */
    @Column(nullable = false)
    val deviceChanged: Boolean,
    /** 사기 확률 점수 (0~1) */
    @Column(nullable = false, precision = 5, scale = 4)
    var fraudScore: BigDecimal,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var fdsStatus: FdsStatus,
    /** 사후 확인된 실제 사기 여부. 아직 확인 전이면 null */
    @Column
    var isActualFraud: Boolean? = null,
    /** 점수에 기여한 피처별 SHAP 값 (JSON 배열 텍스트) */
    @Lob
    @Column(nullable = false)
    var shapReasons: String,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
}
