package com.visiontrade.domain.trade

import com.visiontrade.domain.models.*

/**
 * Deterministic engine for converting a confirmed TradingSignal
 * into an executable trade setup.
 *
 * Rules:
 * - BUY uses support as the protective structure.
 * - SELL uses resistance as the protective structure.
 * - No missing level is fabricated.
 * - WAIT never produces a valid trade setup.
 */
class TradeSetupEngine {

    fun build(signal: TradingSignal): TradeSetup? {
        if (signal.signal == Signal.WAIT) {
            return null
        }

        val entryZone = signal.entryZone ?: return null

        if (
            !entryZone.low.isFinite() ||
            !entryZone.high.isFinite() ||
            entryZone.low <= 0.0 ||
            entryZone.high < entryZone.low
        ) {
            return null
        }

        val support = signal.support
        val resistance = signal.resistance

        return when (signal.signal) {
            Signal.BUY -> buildBuySetup(
                entryZone = entryZone,
                support = support,
                resistance = resistance
            )

            Signal.SELL -> buildSellSetup(
                entryZone = entryZone,
                support = support,
                resistance = resistance
            )

            Signal.WAIT -> null
        }
    }

    private fun buildBuySetup(
        entryZone: PriceZone,
        support: PriceZone?,
        resistance: PriceZone?
    ): TradeSetup? {
        val validSupport = support ?: return null
        val validResistance = resistance ?: return null

        val stopLoss = validSupport.low
        val takeProfit = validResistance.middle
        val entry = entryZone.middle

        if (!validPrice(stopLoss) || !validPrice(takeProfit)) {
            return null
        }

        if (stopLoss >= entry || takeProfit <= entry) {
            return null
        }

        val risk = entry - stopLoss
        val reward = takeProfit - entry

        if (risk <= 0.0 || reward <= 0.0) {
            return null
        }

        val rr = reward / risk

        return TradeSetup(
            direction = TradeDirection.BUY,
            entryZone = entryZone,
            stopLoss = stopLoss,
            takeProfit = takeProfit,
            riskRewardRatio = rr,
            invalidationLevel = stopLoss,
            setupValid = rr > 0.0,
            reason = "BUY setup derived from M15 entry zone, support and resistance."
        )
    }

    private fun buildSellSetup(
        entryZone: PriceZone,
        support: PriceZone?,
        resistance: PriceZone?
    ): TradeSetup? {
        val validSupport = support ?: return null
        val validResistance = resistance ?: return null

        val stopLoss = validResistance.high
        val takeProfit = validSupport.middle
        val entry = entryZone.middle

        if (!validPrice(stopLoss) || !validPrice(takeProfit)) {
            return null
        }

        if (stopLoss <= entry || takeProfit >= entry) {
            return null
        }

        val risk = stopLoss - entry
        val reward = entry - takeProfit

        if (risk <= 0.0 || reward <= 0.0) {
            return null
        }

        val rr = reward / risk

        return TradeSetup(
            direction = TradeDirection.SELL,
            entryZone = entryZone,
            stopLoss = stopLoss,
            takeProfit = takeProfit,
            riskRewardRatio = rr,
            invalidationLevel = stopLoss,
            setupValid = rr > 0.0,
            reason = "SELL setup derived from M15 entry zone, support and resistance."
        )
    }

    private fun validPrice(value: Double): Boolean {
        return value.isFinite() && value > 0.0
    }
}
