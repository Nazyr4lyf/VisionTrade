package com.visiontrade.domain

import com.visiontrade.domain.models.*
import com.visiontrade.domain.trade.TradeSetupEngine
import com.visiontrade.domain.trade.TradeSetupValidator
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TradeSetupTests {

    private val engine = TradeSetupEngine()
    private val validator = TradeSetupValidator()

    private fun buySignal(): TradingSignal {
        val support = PriceZone(99.0, 100.0)
        val resistance = PriceZone(110.0, 112.0)
        val entry = PriceZone(100.0, 101.0)

        return TradingSignal(
            signal = Signal.BUY,
            confidence = Confidence.HIGH,
            trend = Trend.BULLISH,
            conditionsMet = 8,
            totalConditions = 8,
            entryZone = entry,
            support = support,
            resistance = resistance
        )
    }

    private fun sellSignal(): TradingSignal {
        val support = PriceZone(88.0, 90.0)
        val resistance = PriceZone(99.0, 101.0)
        val entry = PriceZone(98.0, 99.0)

        return TradingSignal(
            signal = Signal.SELL,
            confidence = Confidence.HIGH,
            trend = Trend.BEARISH,
            conditionsMet = 8,
            totalConditions = 8,
            entryZone = entry,
            support = support,
            resistance = resistance
        )
    }

    @Test
    fun testBuySetupIsCreatedFromConfirmedSignal() {
        val setup = engine.build(buySignal())

        assertNotNull(setup)
        assertEquals(TradeDirection.BUY, setup.direction)
        assertEquals(99.0, setup.stopLoss)
        assertEquals(111.0, setup.takeProfit)
        assertTrue(setup.setupValid)
        assertTrue(setup.riskRewardRatio > 0.0)
    }

    @Test
    fun testSellSetupIsCreatedFromConfirmedSignal() {
        val setup = engine.build(sellSignal())

        assertNotNull(setup)
        assertEquals(TradeDirection.SELL, setup.direction)
        assertEquals(101.0, setup.stopLoss)
        assertEquals(89.0, setup.takeProfit)
        assertTrue(setup.setupValid)
        assertTrue(setup.riskRewardRatio > 0.0)
    }

    @Test
    fun testWaitNeverCreatesTradeSetup() {
        val signal = buySignal().copy(signal = Signal.WAIT)

        assertNull(engine.build(signal))
    }

    @Test
    fun testBuySetupFailsWithoutSupport() {
        val signal = buySignal().copy(support = null)

        assertNull(engine.build(signal))
    }

    @Test
    fun testBuySetupFailsWithoutResistance() {
        val signal = buySignal().copy(resistance = null)

        assertNull(engine.build(signal))
    }

    @Test
    fun testSellSetupFailsWithoutSupport() {
        val signal = sellSignal().copy(support = null)

        assertNull(engine.build(signal))
    }

    @Test
    fun testSellSetupFailsWithoutResistance() {
        val signal = sellSignal().copy(resistance = null)

        assertNull(engine.build(signal))
    }

    @Test
    fun testGeneratedBuySetupPassesValidator() {
        val setup = engine.build(buySignal())

        assertNotNull(setup)

        val validation = validator.validate(setup)

        assertTrue(validation.valid, validation.reasons.joinToString())
    }

    @Test
    fun testGeneratedSellSetupPassesValidator() {
        val setup = engine.build(sellSignal())

        assertNotNull(setup)

        val validation = validator.validate(setup)

        assertTrue(validation.valid, validation.reasons.joinToString())
    }
}
