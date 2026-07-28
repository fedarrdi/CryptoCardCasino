package com.raretable.casino.paper_trading.trading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.raretable.casino.paper_trading.price.BtcPerpetualMarketSnapshot;

class PaperTradingRiskCalculatorTests
{
    private static final Instant NOW =
        Instant.parse("2026-07-28T08:00:00Z");
    private final PaperTradingRiskCalculator calculator =
        new PaperTradingRiskCalculator();
    private final PerpetualRuleVersion rule = rule();

    @Test
    void tiersAreContinuousAndExactCapsEnterTheNextTier()
    {
        MaintenanceMarginTier below = rule.tierFor(
            new BigDecimal("499999.99")
        );
        MaintenanceMarginTier boundary = rule.tierFor(
            new BigDecimal("500000")
        );

        assertEquals(1, below.tier());
        assertEquals(2, boundary.tier());
        assertEquals(50, boundary.maxLeverage());
        assertDecimal(
            "2000",
            below.maintenanceMargin(new BigDecimal("500000"))
        );
        assertDecimal(
            "2000",
            boundary.maintenanceMargin(new BigDecimal("500000"))
        );
    }

    @Test
    void markControlsEquityMaintenanceAndLiquidationButBookControlsCloseFee()
    {
        PaperTrade trade = trade();
        AccountRiskMetrics tightBook = calculator.calculate(
            new BigDecimal("9800"),
            List.of(trade),
            market("99.9", "100.1", "100"),
            rule
        );
        AccountRiskMetrics wideBook = calculator.calculate(
            new BigDecimal("9800"),
            List.of(trade),
            market("90", "110", "100"),
            rule
        );

        assertDecimal("9800", tightBook.equity());
        assertDecimal("9800", wideBook.equity());
        assertDecimal("2000", tightBook.maintenanceMargin());
        assertDecimal("2000", wideBook.maintenanceMargin());
        assertEquals(
            0,
            tightBook.estimatedLowerLiquidationPrice().compareTo(
                wideBook.estimatedLowerLiquidationPrice()
            )
        );
        assertTrue(
            wideBook.estimatedClosingFee()
                .compareTo(tightBook.estimatedClosingFee()) < 0
        );
        assertTrue(
            tightBook.estimatedLowerLiquidationPrice()
                .compareTo(tightBook.lowerBankruptcyPrice()) > 0
        );
        assertDecimal(
            "98.433734939759",
            tightBook.estimatedLowerLiquidationPrice()
        );
        assertDecimal("98.04", tightBook.lowerBankruptcyPrice());
    }

    private static PaperTrade trade()
    {
        return new PaperTrade(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "BTCUSDT",
            "MARKET",
            "CROSS",
            TradeSide.LONG,
            TradeStatus.OPEN,
            100,
            new BigDecimal("5000"),
            new BigDecimal("500000"),
            new BigDecimal("5000"),
            new BigDecimal("100"),
            null,
            null,
            null,
            0,
            1,
            NOW,
            new BigDecimal("0.0004"),
            null,
            new BigDecimal("200"),
            new BigDecimal("0"),
            new BigDecimal("0"),
            new BigDecimal("0"),
            null,
            null,
            null,
            NOW,
            null
        );
    }

    private static PerpetualRuleVersion rule()
    {
        return new PerpetualRuleVersion(
            1,
            "TEST_V1",
            "BTCUSDT",
            "USD_M_PERPETUAL",
            Instant.parse("2026-01-01T00:00:00Z"),
            new BigDecimal("0.0004"),
            new BigDecimal("0.0125"),
            "FULL",
            "FLOOR_ZERO",
            "LAST",
            List.of(
                new MaintenanceMarginTier(
                    1,
                    new BigDecimal("0"),
                    new BigDecimal("500000"),
                    100,
                    new BigDecimal("0.004"),
                    new BigDecimal("0")
                ),
                new MaintenanceMarginTier(
                    2,
                    new BigDecimal("500000"),
                    new BigDecimal("5000000"),
                    50,
                    new BigDecimal("0.005"),
                    new BigDecimal("500")
                ),
                new MaintenanceMarginTier(
                    3,
                    new BigDecimal("5000000"),
                    new BigDecimal("25000000"),
                    20,
                    new BigDecimal("0.01"),
                    new BigDecimal("25500")
                ),
                new MaintenanceMarginTier(
                    4,
                    new BigDecimal("25000000"),
                    null,
                    10,
                    new BigDecimal("0.025"),
                    new BigDecimal("400500")
                )
            )
        );
    }

    private static BtcPerpetualMarketSnapshot market(
        String bid,
        String ask,
        String mark
    )
    {
        return new BtcPerpetualMarketSnapshot(
            new BigDecimal("100"),
            NOW,
            new BigDecimal(bid),
            new BigDecimal(ask),
            NOW,
            new BigDecimal(mark),
            new BigDecimal("100"),
            BigDecimal.ZERO,
            NOW.plusSeconds(3600),
            NOW
        );
    }

    private static void assertDecimal(String expected, BigDecimal actual)
    {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
