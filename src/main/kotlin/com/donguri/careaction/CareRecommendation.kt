package com.donguri.careaction

import com.donguri.customer.Customer
import com.donguri.customer.CustomerGrade
import com.donguri.transaction.FdsStatus

data class CareRecommendation(
    val product: String,
    val message: String,
) {
    companion object {
        fun forDetection(status: FdsStatus): CareRecommendation? =
            when (status) {
                FdsStatus.NORMAL -> null
                FdsStatus.WARNING ->
                    CareRecommendation(
                        "안심차단 서비스",
                        "평소와 다른 거래가 감지되어 확인 중입니다. 안심차단 서비스로 더 안전하게 이용하실 수 있습니다.",
                    )
                FdsStatus.BLOCKED ->
                    CareRecommendation(
                        "안심차단 서비스 + 우대금리 파킹통장",
                        "고객님의 자산 보호를 위해 거래가 일시 차단되었습니다. 불편을 드려 죄송하며 우대금리 파킹통장을 안내해 드립니다.",
                    )
            }

        fun forFalsePositive(customer: Customer): CareRecommendation =
            if (customer.grade == CustomerGrade.VIP) {
                CareRecommendation(
                    "수수료 면제 + VIP 전담 상담",
                    "정상 거래가 잘못 차단되어 큰 불편을 드렸습니다. 수수료 면제와 VIP 전담 상담으로 보답하겠습니다.",
                )
            } else {
                CareRecommendation(
                    "우대금리 파킹통장 + 수수료 면제 쿠폰",
                    "정상 거래가 잘못 차단되어 불편을 드렸습니다. 우대금리 파킹통장과 수수료 면제 쿠폰을 드립니다.",
                )
            }
    }
}
