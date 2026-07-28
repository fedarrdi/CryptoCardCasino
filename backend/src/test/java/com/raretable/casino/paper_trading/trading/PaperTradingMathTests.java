package com.raretable.casino.paper_trading.trading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.raretable.casino.paper_trading.price.BtcQuote;

class PaperTradingMathTests
{
    private static final BtcQuote QUOTE = new BtcQuote(
        new BigDecimal("99.000000000000"),
        new BigDecimal("100.000000000000")
    );

    @Test
    void longAndShortExecuteOnOppositeSidesOfTheSpread()
    {
        assertDecimal("100", TradeSide.LONG.openingPrice(QUOTE));
        assertDecimal("99", TradeSide.LONG.closingPrice(QUOTE));
        assertDecimal("99", TradeSide.SHORT.openingPrice(QUOTE));
        assertDecimal("100", TradeSide.SHORT.closingPrice(QUOTE));

        assertDecimal(
            "-100.00000000",
            PaperTradingMath.pnl(
                TradeSide.LONG,
                new BigDecimal("100"),
                new BigDecimal("99"),
                new BigDecimal("100")
            )
        );
        assertDecimal(
            "-100.00000000",
            PaperTradingMath.pnl(
                TradeSide.SHORT,
                new BigDecimal("99"),
                new BigDecimal("100"),
                new BigDecimal("100")
            )
        );
    }

    @Test
    void quantityPnlAndRoeUseDeterministicRounding()
    {
        BigDecimal quantity = PaperTradingMath.quantity(
            new BigDecimal("1000"),
            new BigDecimal("3")
        );
        BigDecimal pnl = PaperTradingMath.pnl(
            TradeSide.LONG,
            new BigDecimal("3"),
            new BigDecimal("3.01"),
            quantity
        );

        assertEquals("333.333333333333", quantity.toPlainString());
        assertEquals("3.33333333", pnl.toPlainString());
        assertEquals(
            "0.3333",
            PaperTradingMath.roePercent(pnl, new BigDecimal("1000"))
                .toPlainString()
        );
    }

    @Test
    void riskControlDirectionsAndBoundariesAreExplicit()
    {
        TradeSide.LONG.validateRiskControls(
            new BigDecimal("99.5"),
            new BigDecimal("90"),
            new BigDecimal("110")
        );
        TradeSide.SHORT.validateRiskControls(
            new BigDecimal("99.5"),
            new BigDecimal("110"),
            new BigDecimal("90")
        );

        assertThrows(
            IllegalArgumentException.class,
            () -> TradeSide.LONG.validateRiskControls(
                new BigDecimal("99.5"),
                new BigDecimal("99.5"),
                null
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> TradeSide.SHORT.validateRiskControls(
                new BigDecimal("99.5"),
                null,
                new BigDecimal("99.5")
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> TradeSide.LONG.validateRiskControls(
                new BigDecimal("99.5"),
                null,
                new BigDecimal("99.5")
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> TradeSide.SHORT.validateRiskControls(
                new BigDecimal("99.5"),
                new BigDecimal("99.5"),
                null
            )
        );

        assertTrue(TradeSide.LONG.stopLossTriggered(
            new BigDecimal("90"),
            new BigDecimal("90")
        ));
        assertTrue(TradeSide.LONG.takeProfitTriggered(
            new BigDecimal("110"),
            new BigDecimal("110")
        ));
        assertTrue(TradeSide.SHORT.stopLossTriggered(
            new BigDecimal("110"),
            new BigDecimal("110")
        ));
        assertTrue(TradeSide.SHORT.takeProfitTriggered(
            new BigDecimal("90"),
            new BigDecimal("90")
        ));
        assertFalse(TradeSide.LONG.stopLossTriggered(
            new BigDecimal("90.01"),
            new BigDecimal("90")
        ));
        assertFalse(TradeSide.SHORT.takeProfitTriggered(
            new BigDecimal("90.01"),
            new BigDecimal("90")
        ));
    }

    private static void assertDecimal(String expected, BigDecimal actual)
    {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
