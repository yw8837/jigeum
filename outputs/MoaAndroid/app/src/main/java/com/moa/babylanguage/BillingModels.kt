package com.moa.babylanguage

enum class BillingPlan(val basePlanId: String) {
    MONTHLY("monthly"),
    THREE_MONTH_PREPAID("three-month-prepaid")
}

enum class BillingStatus {
    UNAVAILABLE, CONNECTING, READY, PURCHASING, PENDING, VERIFYING, PREMIUM, ERROR
}

data class BillingProduct(
    val productId: String,
    val title: String,
    val description: String
)

data class BillingOption(
    val plan: BillingPlan,
    val productId: String,
    val basePlanId: String,
    val formattedPrice: String,
    val billingPeriod: String,
    val renewalType: String
)

data class BillingUiState(
    val products: List<BillingProduct> = emptyList(),
    val options: List<BillingOption> = emptyList(),
    val status: BillingStatus = BillingStatus.CONNECTING,
    val isPremium: Boolean = false,
    val message: String? = null
)
