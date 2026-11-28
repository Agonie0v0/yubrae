package com.yubrae.app.capture.screen

import com.yubrae.app.data.db.AccountAliasEntity
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.CategoryEntity

/**
 * 纯 JVM、线程安全的「电商/外卖提交订单(Stage 0) -> 支付收银台(Stage 1) -> 支付/履约确认」内存状态机。
 * - Stage 0 有效期 60 秒（用于衔接 Stage 1 保留真实门店/商品/外卖标记）；
 * - Stage 1 收银台有效期 35 秒（用于衔接付款成功无金额闪帧或跳回淘宝查看订单/外卖进度首屏）；
 * - 无 Stage 1 的免密支付 Stage 0 直接确认有效期 20 秒且仅限明确已付/接单强信号；
 * - 一旦用户取消支付退回待付款/失败页，或单帧解析已直接落账，或一次性消费完成，立即清空防止误触与重复记账。
 */
class CheckoutSessionTracker {

    private val lock = Any()
    private var pendingContext: PendingCheckoutContext? = null
    private var originEcommercePackage: String = ""

    fun recordCheckout(context: PendingCheckoutContext) {
        synchronized(lock) {
            val nowMillis = context.capturedAtMillis
            val existing = peekActiveLocked(nowMillis)

            if (context.isCashierStage) {
                if (context.origAmountMicros <= 0L) return
                pendingContext = if (existing != null) {
                    if (!existing.isCashierStage && ScreenBillParser.isEcommerceOrDeliveryPackage(existing.sourcePackage)) {
                        originEcommercePackage = existing.sourcePackage
                    }
                    mergeIntoCashierContext(existing = existing, incoming = context)
                } else {
                    originEcommercePackage = if (ScreenBillParser.isEcommerceOrDeliveryPackage(context.sourcePackage)) {
                        context.sourcePackage
                    } else {
                        ""
                    }
                    context
                }
                return
            }

            // Stage 0（提交订单 / 确认订单）
            if (existing != null && existing.isCashierStage) {
                // 收银台弹窗仍处于有效期内，防止底层电商页面内容刷新把 Stage 1 降级为 Stage 0；仅补充更具体的门店/商品/外卖线索
                if (originEcommercePackage.isBlank() && ScreenBillParser.isEcommerceOrDeliveryPackage(context.sourcePackage)) {
                    originEcommercePackage = context.sourcePackage
                }
                val enrichedMerchant = if (ScreenBillParser.isGenericCheckoutMerchant(existing.merchant) &&
                    !ScreenBillParser.isGenericCheckoutMerchant(context.merchant)
                ) {
                    context.merchant
                } else {
                    existing.merchant
                }
                val enrichedGoods = existing.goodsSummary.ifBlank { context.goodsSummary }
                val enrichedTakeout = existing.isTakeout || context.isTakeout
                pendingContext = existing.copy(
                    merchant = enrichedMerchant,
                    goodsSummary = enrichedGoods,
                    isTakeout = enrichedTakeout
                )
                return
            }

            originEcommercePackage = context.sourcePackage
            pendingContext = if (existing != null &&
                !existing.isCashierStage &&
                existing.sourcePackage.equals(context.sourcePackage, ignoreCase = true) &&
                isSameOrRefiningMerchant(existing.merchant, context.merchant)
            ) {
                PendingCheckoutContext(
                    sourcePackage = context.sourcePackage,
                    merchant = choosePreferredMerchant(existing.merchant, context.merchant),
                    goodsSummary = context.goodsSummary.ifBlank { existing.goodsSummary },
                    origAmountMicros = if (context.origAmountMicros > 0L) context.origAmountMicros else existing.origAmountMicros,
                    origCurrency = context.origCurrency.ifBlank { existing.origCurrency },
                    discountAmountMicros = if (context.discountAmountMicros > 0L) {
                        context.discountAmountMicros
                    } else {
                        existing.discountAmountMicros
                    },
                    paymentMethodText = context.paymentMethodText.ifBlank { existing.paymentMethodText },
                    accountHint = if (context.paymentMethodText.isNotBlank()) {
                        context.accountHint
                    } else {
                        context.accountHint.ifBlank { existing.accountHint }
                    },
                    isTakeout = existing.isTakeout || context.isTakeout,
                    capturedAtMillis = context.capturedAtMillis,
                    isCashierStage = false
                )
            } else {
                context
            }
        }
    }

    fun markExplicitCancelOrUnpaid(nowMillis: Long) {
        synchronized(lock) {
            pendingContext = null
            originEcommercePackage = ""
        }
    }

    fun clearOnDirectCapture(amountMicros: Long = 0L) {
        synchronized(lock) {
            pendingContext = null
            originEcommercePackage = ""
        }
    }

    fun reset() {
        synchronized(lock) {
            pendingContext = null
            originEcommercePackage = ""
        }
    }

    fun hasActivePendingCheckout(nowMillis: Long): Boolean = synchronized(lock) {
        peekActiveLocked(nowMillis) != null
    }

    fun peekActivePendingCheckout(nowMillis: Long): PendingCheckoutContext? = synchronized(lock) {
        peekActiveLocked(nowMillis)
    }

    fun consumeIfConfirmed(
        snapshot: ScreenSnapshot,
        signal: CheckoutCompletionSignal,
        parser: ScreenBillParser,
        aliases: List<AccountAliasEntity> = emptyList(),
        accounts: List<AccountEntity> = emptyList(),
        categories: List<CategoryEntity> = emptyList()
    ): ScreenParseOutcome? = synchronized(lock) {
        val nowMillis = snapshot.capturedAtMillis
        val current = peekActiveLocked(nowMillis) ?: return@synchronized null

        val elapsed = nowMillis - current.capturedAtMillis
        if (elapsed < -MAX_BACKWARD_CLOCK_SKEW_MILLIS) {
            return@synchronized null
        }

        if (current.isCashierStage) {
            if (elapsed > STAGE1_TTL_MILLIS) {
                pendingContext = null
                originEcommercePackage = ""
                return@synchronized null
            }
        } else {
            // 无 Stage 1 的免密 Stage 0 仅在 20 秒内且收到明确已付/接单强信号时放行
            if (elapsed > STAGE0_DIRECT_CONFIRM_TTL_MILLIS || !signal.isStrongPaidSignal) {
                return@synchronized null
            }
        }

        val resolvedAmountMicros = if (current.isCashierStage && current.origAmountMicros > 0L) {
            current.origAmountMicros
        } else {
            signal.confirmedAmountMicros?.takeIf { it > 0L } ?: current.origAmountMicros
        }
        if (resolvedAmountMicros <= 0L) {
            return@synchronized null
        }

        val outcome = parser.buildCheckoutConfirmedOutcome(
            snapshot = snapshot,
            pending = current,
            signal = signal,
            aliases = aliases,
            accounts = accounts,
            categories = categories,
            originSourcePackage = originEcommercePackage
        ) ?: return@synchronized null

        if (outcome.draft == null || outcome.draft.origAmountMicros <= 0L) {
            return@synchronized null
        }

        // 一次性消费后立即清空，杜绝跨页/重绘重复触发
        pendingContext = null
        originEcommercePackage = ""
        outcome
    }

    private fun peekActiveLocked(nowMillis: Long): PendingCheckoutContext? {
        val current = pendingContext ?: return null
        val ttl = if (current.isCashierStage) STAGE1_TTL_MILLIS else STAGE0_TTL_MILLIS
        val elapsed = nowMillis - current.capturedAtMillis
        if (elapsed > ttl) {
            pendingContext = null
            originEcommercePackage = ""
            return null
        }
        return current
    }

    private fun mergeIntoCashierContext(
        existing: PendingCheckoutContext,
        incoming: PendingCheckoutContext
    ): PendingCheckoutContext {
        val mergedMerchant = choosePreferredMerchant(existing.merchant, incoming.merchant)
        val mergedGoods = incoming.goodsSummary.ifBlank { existing.goodsSummary }
        val mergedTakeout = existing.isTakeout || incoming.isTakeout
        val mergedDiscount = if (incoming.discountAmountMicros > 0L) {
            incoming.discountAmountMicros
        } else {
            existing.discountAmountMicros
        }
        val mergedPaymentMethod = if (incoming.paymentMethodText.isNotBlank()) {
            incoming.paymentMethodText
        } else {
            existing.paymentMethodText
        }
        // 用户切换付款方式（如银行卡6688 -> 花呗）时以新付款方式的 accountHint 为准，避免残留旧卡尾号
        val mergedAccountHint = if (incoming.paymentMethodText.isNotBlank()) {
            incoming.accountHint
        } else {
            incoming.accountHint.ifBlank { existing.accountHint }
        }
        return PendingCheckoutContext(
            sourcePackage = incoming.sourcePackage,
            merchant = mergedMerchant,
            goodsSummary = mergedGoods,
            origAmountMicros = incoming.origAmountMicros,
            origCurrency = incoming.origCurrency.ifBlank { existing.origCurrency },
            discountAmountMicros = mergedDiscount,
            paymentMethodText = mergedPaymentMethod,
            accountHint = mergedAccountHint,
            isTakeout = mergedTakeout,
            capturedAtMillis = incoming.capturedAtMillis,
            isCashierStage = true
        )
    }

    private fun choosePreferredMerchant(existing: String, incoming: String): String {
        val incomingSpecific = !ScreenBillParser.isGenericCheckoutMerchant(incoming)
        val existingSpecific = !ScreenBillParser.isGenericCheckoutMerchant(existing)
        return when {
            incomingSpecific -> incoming
            existingSpecific -> existing
            incoming.isNotBlank() -> incoming
            else -> existing
        }
    }

    private fun isSameOrRefiningMerchant(existing: String, incoming: String): Boolean {
        if (existing.isBlank() || incoming.isBlank()) return true
        if (ScreenBillParser.isGenericCheckoutMerchant(existing) || ScreenBillParser.isGenericCheckoutMerchant(incoming)) {
            return true
        }
        return existing.equals(incoming, ignoreCase = true)
    }

    companion object {
        const val STAGE0_TTL_MILLIS: Long = 60_000L
        const val STAGE1_TTL_MILLIS: Long = 35_000L
        const val STAGE0_DIRECT_CONFIRM_TTL_MILLIS: Long = 20_000L
        private const val MAX_BACKWARD_CLOCK_SKEW_MILLIS: Long = 5_000L
    }
}
