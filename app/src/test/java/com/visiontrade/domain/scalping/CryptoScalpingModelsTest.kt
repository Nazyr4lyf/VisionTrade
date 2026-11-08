package com.visiontrade.domain.scalping

import java.math.BigDecimal
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CryptoScalpingModelsTest {

    @Test
    fun defaultRiskPolicyLocksOnePercentAndMinimumTwoR() {
        val policy = ScalpingRiskPolicy()

        assertEquals(BigDecimal("0.01"), policy.riskFraction)
        assertEquals(BigDecimal("2.0"), policy.minimumNetRiskReward)
        assertEquals(BigDecimal("3.0"), policy.preferredMaximumRiskReward)
    }

    @Test
    fun riskPolicyRejectsMinimumRewardRiskBelowTwo() {
        assertFailsWith<IllegalArgumentException> {
            ScalpingRiskPolicy(minimumNetRiskReward = BigDecimal("1.99"))
        }
    }

    @Test
    fun riskPolicyRejectsRiskAboveOneHundredPercent() {
        assertFailsWith<IllegalArgumentException> {
            ScalpingRiskPolicy(riskFraction = BigDecimal("1.01"))
        }
    }

    @Test
    fun supportResistanceZoneCalculatesMidpointAndContainsPrice() {
        val zone = SupportResistanceZone(
            low = BigDecimal("100"),
            high = BigDecimal("102"),
            touches = 3,
            lastConfirmedAtMillis = 1_800_000_000_000L
        )

        assertEquals(BigDecimal("101"), zone.midpoint)
        assertTrue(zone.contains(BigDecimal("101")))
        assertFalse(zone.contains(BigDecimal("103")))
    }

    @Test
    fun exchangeInstrumentRequiresPositiveExchangeFilters() {
        assertFailsWith<IllegalArgumentException> {
            ExchangeInstrument(
                symbol = "BTCUSDT",
                marketType = CryptoMarketType.SPOT,
                baseCoin = "BTC",
                quoteCoin = "USDT",
                priceTick = BigDecimal.ZERO,
                quantityStep = BigDecimal("0.0001"),
                minimumQuantity = BigDecimal("0.0001"),
                minimumNotional = BigDecimal("5")
            )
        }
    }

    @Test
    fun candleRejectsCloseOutsideHighLowRange() {
        assertFailsWith<IllegalArgumentException> {
            CryptoCandle(
                timestampMillis = 1_800_000_000_000L,
                open = BigDecimal("100"),
                high = BigDecimal("105"),
                low = BigDecimal("98"),
                close = BigDecimal("106"),
                volume = BigDecimal("10"),
                turnover = BigDecimal("1000"),
                timeframe = MarketTimeframe.ONE_MINUTE,
                isClosed = true
            )
        }
    }

    @Test
    fun strategyConfigRequiresAdequateHistory() {
        assertFailsWith<IllegalArgumentException> {
            ScalpingStrategyConfig(fiveMinuteLookback = 30)
        }
    }
}
