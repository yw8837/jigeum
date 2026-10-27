package com.moa.babylanguage

import com.android.billingclient.api.ProductDetails

internal data class OfferPricePhase(val priceMicros: Long, val currency: String,
    val formattedPrice: String, val period: String, val recurrence: Int, val cycles: Int)

/** Recognize only approved terms; never label an unexpected phase as the launch discount. */
internal fun checkedBillingOption(productId: String, basePlanId: String, offerId: String?,
    phases: List<OfferPricePhase>): BillingOption? {
    if (productId != PurchaseVerifier.PRODUCT_ID) return null
    if (offerId == LAUNCH_OFFER_ID && basePlanId == "monthly" && phases.size == 2) {
        val intro = phases[0]; val renewal = phases[1]
        if (intro.currency != "KRW" || renewal.currency != "KRW" || intro.priceMicros != 2_450_000_000L ||
            renewal.priceMicros != 4_900_000_000L || intro.period != "P1M" || renewal.period != "P1M" ||
            intro.recurrence != ProductDetails.RecurrenceMode.FINITE_RECURRING || intro.cycles != 2 ||
            renewal.recurrence != ProductDetails.RecurrenceMode.INFINITE_RECURRING) return null
        return BillingOption(BillingPlan.LAUNCH_MONTHLY, productId, basePlanId, intro.formattedPrice,
            "P1M", "AUTO_RENEWING", renewal.formattedPrice, 2)
    }
    if (offerId != null) return null
    val phase = phases.singleOrNull() ?: return null
    val plan = when {
        basePlanId == "monthly" && phase.period == "P1M" && phase.recurrence == ProductDetails.RecurrenceMode.INFINITE_RECURRING -> BillingPlan.MONTHLY
        basePlanId == "three-month-prepaid" && phase.period == "P3M" && phase.recurrence == ProductDetails.RecurrenceMode.NON_RECURRING -> BillingPlan.THREE_MONTH_PREPAID
        else -> return null
    }
    return BillingOption(plan, productId, basePlanId, phase.formattedPrice, phase.period,
        if (plan == BillingPlan.MONTHLY) "AUTO_RENEWING" else "PREPAID")
}
