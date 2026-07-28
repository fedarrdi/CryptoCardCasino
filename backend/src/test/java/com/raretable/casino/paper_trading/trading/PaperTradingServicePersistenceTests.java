package com.raretable.casino.paper_trading.trading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import com.raretable.casino.InfrastructureTestConfiguration;
import com.raretable.casino.paper_trading.api.OpenPositionRequest;
import com.raretable.casino.paper_trading.api.PaperPositionResponse;
import com.raretable.casino.paper_trading.api.PaperTradingPortfolioResponse;
import com.raretable.casino.paper_trading.api.PreviewPositionRequest;
import com.raretable.casino.paper_trading.api.UpdateRiskControlsRequest;
import com.raretable.casino.paper_trading.market_data.BinanceFundingRate;
import com.raretable.casino.paper_trading.market_data.BinanceFundingRateSource;
import com.raretable.casino.paper_trading.price.BinanceWrapper;
import com.raretable.casino.paper_trading.price.BtcQuote;
import com.raretable.casino.paper_trading.price.BtcPerpetualMarketSnapshot;
import com.raretable.casino.user.User;
import com.raretable.casino.user.UserService;

@SpringBootTest
@ActiveProfiles("test")
@Import({
    InfrastructureTestConfiguration.class,
    PaperTradingServicePersistenceTests.PriceTestConfiguration.class
})
class PaperTradingServicePersistenceTests
{
    private static final Instant NOW =
        Instant.parse("2026-07-27T12:00:00Z");

    @Autowired
    private PaperTradingService service;

    @Autowired
    private JdbcPaperTradingRepository repository;

    @Autowired
    private PaperFundingService fundingService;

    @Autowired
    private StubFundingRateSource fundingSource;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private UserService userService;

    @Autowired
    private StubBinanceWrapper binance;

    @Test
    void separatesMarkPnlFromExecutableCloseAndPersistsNetPnl()
    {
        UUID userId = newUser("01").getUniqueId();
        binance.quote("99", "100");

        PaperPositionResponse longPosition = service.openPosition(
            userId,
            open(TradeSide.LONG, "1000", 10, null, null)
        ).openPositions().getFirst();
        PaperPositionResponse shortPosition = service.openPosition(
            userId,
            open(TradeSide.SHORT, "990", 10, null, null)
        ).openPositions().stream()
            .filter(position -> position.side() == TradeSide.SHORT)
            .findFirst()
            .orElseThrow();

        assertDecimal("100", longPosition.entryPrice());
        assertDecimal("99.5", longPosition.markPrice());
        assertDecimal("100", longPosition.quantity());
        assertDecimal("-50", longPosition.unrealizedPnl());
        assertDecimal("-5", longPosition.unrealizedRoePercent());
        assertDecimal("4", longPosition.entryFee());
        assertDecimal("-107.96", longPosition.estimatedNetPnl());
        assertEquals("MARKET", longPosition.orderType());
        assertEquals("CROSS", longPosition.marginMode());

        assertDecimal("99", shortPosition.entryPrice());
        assertDecimal("99.5", shortPosition.markPrice());
        assertDecimal("100", shortPosition.quantity());
        assertDecimal("-50", shortPosition.unrealizedPnl());
        assertDecimal("3.96", shortPosition.entryFee());

        binance.quote("109", "110");
        PaperTradingPortfolioResponse preview = service.getPortfolio(userId, 50);
        PaperPositionResponse longPreview = position(
            preview.openPositions(),
            longPosition.id()
        );
        PaperPositionResponse shortPreview = position(
            preview.openPositions(),
            shortPosition.id()
        );

        assertDecimal("950", longPreview.unrealizedPnl());
        assertDecimal("95", longPreview.unrealizedRoePercent());
        assertDecimal("-1050", shortPreview.unrealizedPnl());
        assertDecimal("-106.0606", shortPreview.unrealizedRoePercent());
        assertDecimal("-100", preview.account().grossUnrealizedPnl());
        assertDecimal("9892.04", preview.account().equity());
        assertDecimal("2190", preview.account().initialMargin());
        assertDecimal("8.76", preview.account().estimatedClosingFee());
        assertDecimal("7693.28", preview.account().availableMargin());

        PaperTradingPortfolioResponse afterLongClose =
            service.closePosition(userId, longPosition.id());
        PaperPositionResponse closedLong = position(
            afterLongClose.closedTrades(),
            longPosition.id()
        );

        assertEquals(TradeStatus.CLOSED, closedLong.status());
        assertEquals(TradeCloseReason.USER, closedLong.closeReason());
        assertDecimal("109", closedLong.exitPrice());
        assertDecimal("900", closedLong.grossRealizedPnl());
        assertDecimal("891.64", closedLong.realizedPnl());
        assertDecimal("10887.68", repository.getAccount(userId).balanceUsd());

        service.closePosition(userId, shortPosition.id());

        assertDecimal("9783.28", repository.getAccount(userId).balanceUsd());
        assertEquals(2, repository.findClosedTrades(userId, 50).size());
        assertTrue(repository.findOpenTrades(userId).isEmpty());
    }

    @Test
    void crossMarginAdmissionIncludesUnrealizedPnlAndIsSerialized()
        throws Exception
    {
        UUID userId = newUser("02").getUniqueId();
        binance.quote("100", "100");
        service.openPosition(
            userId,
            open(TradeSide.LONG, "6000", 1, null, null)
        );

        binance.quote("90", "90");
        PaperTradingPortfolioResponse losingPortfolio =
            service.getPortfolio(userId, 50);
        assertDecimal(
            "-600",
            losingPortfolio.account().grossUnrealizedPnl()
        );
        assertDecimal("3995.44", losingPortfolio.account().availableMargin());
        assertThrows(
            InsufficientPaperMarginException.class,
            () -> service.openPosition(
                userId,
                open(TradeSide.LONG, "4100", 1, null, null)
            )
        );

        UUID concurrentUserId = newUser("03").getUniqueId();
        binance.quote("100", "100");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try
        {
            Future<?> first = executor.submit(() ->
                openAfter(start, concurrentUserId, "7000")
            );
            Future<?> second = executor.submit(() ->
                openAfter(start, concurrentUserId, "7000")
            );
            start.countDown();

            int successes = 0;
            int insufficientMarginFailures = 0;
            for (Future<?> future : List.of(first, second))
            {
                try
                {
                    future.get();
                    successes++;
                }
                catch (ExecutionException exception)
                {
                    assertInstanceOf(
                        InsufficientPaperMarginException.class,
                        exception.getCause()
                    );
                    insufficientMarginFailures++;
                }
            }

            assertEquals(1, successes);
            assertEquals(1, insufficientMarginFailures);
            assertEquals(1, repository.findOpenTrades(concurrentUserId).size());
        }
        finally
        {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentCloseCreditsRealizedPnlExactlyOnce() throws Exception
    {
        UUID userId = newUser("04").getUniqueId();
        binance.quote("100", "100");
        UUID positionId = service.openPosition(
            userId,
            open(TradeSide.LONG, "1000", 10, null, null)
        ).openPositions().getFirst().id();
        binance.quote("110", "110");

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try
        {
            Future<?> first = executor.submit(() ->
                closeAfter(start, userId, positionId)
            );
            Future<?> second = executor.submit(() ->
                closeAfter(start, userId, positionId)
            );
            start.countDown();

            int successes = 0;
            int alreadyClosedFailures = 0;
            for (Future<?> future : List.of(first, second))
            {
                try
                {
                    future.get();
                    successes++;
                }
                catch (ExecutionException exception)
                {
                    assertInstanceOf(
                        PaperPositionNotOpenException.class,
                        exception.getCause()
                    );
                    alreadyClosedFailures++;
                }
            }

            assertEquals(1, successes);
            assertEquals(1, alreadyClosedFailures);
            assertDecimal(
                "10991.6",
                repository.getAccount(userId).balanceUsd()
            );
            assertEquals(1, repository.findClosedTrades(userId, 50).size());
            assertTrue(repository.findOpenTrades(userId).isEmpty());
        }
        finally
        {
            executor.shutdownNow();
        }
    }

    @ParameterizedTest
    @MethodSource("riskControlCases")
    void stopLossAndTakeProfitTriggerAtInclusiveBoundaryOnlyOnce(
        String walletSuffix,
        TradeSide side,
        String margin,
        String stopLoss,
        String takeProfit,
        String triggerBid,
        String triggerAsk,
        String observedPrice,
        TradeCloseReason expectedReason,
        String expectedBalance
    )
    {
        UUID userId = newUser(walletSuffix).getUniqueId();
        binance.quote("99", "100");
        UUID positionId = service.openPosition(
            userId,
            open(side, margin, 10, stopLoss, takeProfit)
        ).openPositions().getFirst().id();

        binance.quote(triggerBid, triggerAsk);
        service.processRiskControls(new BigDecimal(observedPrice), NOW);
        service.processRiskControls(new BigDecimal(observedPrice), NOW);

        PaperTrade closed = repository.findTrade(positionId).orElseThrow();
        assertEquals(TradeStatus.CLOSED, closed.status());
        assertEquals(expectedReason, closed.closeReason());
        assertDecimal(expectedBalance, repository.getAccount(userId).balanceUsd());
        assertEquals(1, repository.findClosedTrades(userId, 50).size());
    }

    @Test
    void observedTriggerReasonIsPreservedWhenTheExecutableQuoteRebounds()
    {
        UUID userId = newUser("12").getUniqueId();
        binance.quote("99", "100");
        UUID positionId = service.openPosition(
            userId,
            open(TradeSide.LONG, "1000", 10, "90", "110")
        ).openPositions().getFirst().id();

        binance.quote("95", "96");
        service.processRiskControls(new BigDecimal("90"), NOW);

        PaperTrade closed = repository.findTrade(positionId).orElseThrow();
        assertEquals(TradeStatus.CLOSED, closed.status());
        assertEquals(TradeCloseReason.STOP_LOSS, closed.closeReason());
        assertDecimal("95", closed.exitPrice());
        assertDecimal("-500", closed.grossRealizedPnl());
        assertDecimal("-507.8", closed.realizedPnl());
        assertDecimal("9492.2", repository.getAccount(userId).balanceUsd());
    }

    @Test
    void repeatedClientOrderIdIsIdempotentButCannotChangeItsIntent()
    {
        UUID userId = newUser("13").getUniqueId();
        UUID clientOrderId = UUID.fromString(
            "9bde1730-3fd9-4faf-9890-a5d3e8e1c244"
        );
        binance.quote("99", "100");
        OpenPositionRequest order = new OpenPositionRequest(
            clientOrderId,
            TradeSide.LONG,
            10,
            new BigDecimal("1000"),
            new BigDecimal("90"),
            new BigDecimal("110")
        );

        UUID firstPositionId = service.openPosition(userId, order)
            .openPositions()
            .getFirst()
            .id();
        UUID repeatedPositionId = service.openPosition(userId, order)
            .openPositions()
            .getFirst()
            .id();

        assertEquals(firstPositionId, repeatedPositionId);
        assertEquals(1, repository.findOpenTrades(userId).size());
        assertThrows(
            PaperOrderIdConflictException.class,
            () -> service.openPosition(
                userId,
                new OpenPositionRequest(
                    clientOrderId,
                    TradeSide.LONG,
                    11,
                    new BigDecimal("1000"),
                    new BigDecimal("90"),
                    new BigDecimal("110")
                )
            )
        );
        assertEquals(1, repository.findOpenTrades(userId).size());
    }

    @Test
    void riskControlHistoryMatchesSignalsToTheControlsEffectiveAtThatTime()
    {
        UUID userId = newUser("14").getUniqueId();
        binance.quote("99", "100");
        UUID positionId = service.openPosition(
            userId,
            open(TradeSide.LONG, "1000", 10, "90", "110")
        ).openPositions().getFirst().id();
        PaperTrade initialTrade = repository.findTrade(positionId)
            .orElseThrow();

        repository.updateRiskControls(
            initialTrade,
            new BigDecimal("80"),
            new BigDecimal("120"),
            NOW.plusSeconds(2)
        );

        List<TriggeredPaperTrade> beforeEdit =
            repository.findTriggeredTrades(
                new BigDecimal("85"),
                NOW.plusSeconds(1)
            );
        assertEquals(1, beforeEdit.size());
        assertEquals(TradeCloseReason.STOP_LOSS, beforeEdit.getFirst().reason());
        assertTrue(repository.findTriggeredTrades(
            new BigDecimal("85"),
            NOW.plusSeconds(3)
        ).isEmpty());

        binance.quote("85", "86");
        service.processRiskControls(
            new BigDecimal("85"),
            NOW.plusSeconds(1)
        );

        PaperTrade closed = repository.findTrade(positionId).orElseThrow();
        assertEquals(TradeStatus.CLOSED, closed.status());
        assertEquals(TradeCloseReason.STOP_LOSS, closed.closeReason());
    }

    @Test
    void usersCannotReadMutateOrCloseEachOthersPositions()
    {
        UUID ownerId = newUser("09").getUniqueId();
        UUID otherUserId = newUser("10").getUniqueId();
        binance.quote("99", "100");
        UUID positionId = service.openPosition(
            ownerId,
            open(TradeSide.LONG, "1000", 2, "90", "110")
        ).openPositions().getFirst().id();

        PaperTradingPortfolioResponse otherPortfolio =
            service.getPortfolio(otherUserId, 50);
        assertTrue(otherPortfolio.openPositions().isEmpty());
        assertTrue(otherPortfolio.closedTrades().isEmpty());
        assertDecimal("10000", otherPortfolio.account().balance());

        assertThrows(
            PaperPositionNotFoundException.class,
            () -> service.updateRiskControls(
                otherUserId,
                positionId,
                new UpdateRiskControlsRequest(
                    new BigDecimal("91"),
                    new BigDecimal("109")
                )
            )
        );
        assertThrows(
            PaperPositionNotFoundException.class,
            () -> service.closePosition(otherUserId, positionId)
        );
        assertEquals(
            TradeStatus.OPEN,
            repository.findTrade(positionId).orElseThrow().status()
        );
    }

    @Test
    void rejectsInvalidLeveragePrecisionAndRiskControlDirections()
    {
        UUID userId = newUser("11").getUniqueId();
        binance.quote("99", "100");

        assertThrows(
            IllegalArgumentException.class,
            () -> service.openPosition(
                userId,
                open(TradeSide.LONG, "1000", 0, null, null)
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> service.openPosition(
                userId,
                open(TradeSide.LONG, "1000", 101, null, null)
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> service.openPosition(
                userId,
                open(
                    TradeSide.LONG,
                    "1.000000001",
                    1,
                    null,
                    null
                )
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> service.openPosition(
                userId,
                open(TradeSide.LONG, "1000", 1, "100", null)
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> service.openPosition(
                userId,
                open(TradeSide.SHORT, "1000", 1, null, "99.5")
            )
        );
        assertTrue(repository.findOpenTrades(userId).isEmpty());
    }

    @Test
    void previewEnforcesAggregateTierLeverageAndShowsCrossLiquidation()
    {
        UUID userId = newUser("15").getUniqueId();
        binance.quote("100", "100");

        assertThrows(
            IllegalArgumentException.class,
            () -> service.previewPosition(
                userId,
                new PreviewPositionRequest(
                    TradeSide.LONG,
                    100,
                    new BigDecimal("5000"),
                    null,
                    null
                )
            )
        );

        var preview = service.previewPosition(
            userId,
            new PreviewPositionRequest(
                TradeSide.LONG,
                100,
                new BigDecimal("4999"),
                null,
                null
            )
        );

        assertEquals("RARETABLE_BTCUSDT_V1", preview.ruleVersion());
        assertDecimal("499900", preview.notionalUsd());
        assertDecimal("199.96", preview.entryFee());
        assertTrue(preview.maxOrderMargin().compareTo(
            new BigDecimal("5000")
        ) < 0);
        assertTrue(
            preview.estimatedLiquidationPrice()
                .compareTo(preview.bankruptcyPrice()) > 0
        );
        assertTrue(
            preview.postOrderMaintenanceMarginRatioPercent().signum() > 0
        );

        service.openPosition(
            userId,
            open(TradeSide.LONG, "4999", 100, null, null)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> service.previewPosition(
                userId,
                new PreviewPositionRequest(
                    TradeSide.LONG,
                    1,
                    new BigDecimal("101"),
                    null,
                    null
                )
            )
        );
    }

    @Test
    void markLiquidationClosesTheWholeCrossAccountAndFloorsDebt()
    {
        UUID userId = newUser("16").getUniqueId();
        binance.quote("100", "100");
        UUID firstPositionId = service.openPosition(
            userId,
            open(TradeSide.LONG, "4500", 50, null, null)
        ).openPositions().getFirst().id();
        service.openPosition(
            userId,
            open(TradeSide.LONG, "4500", 50, null, null)
        );
        List<UUID> positionIds = repository.findOpenTrades(userId).stream()
            .map(PaperTrade::id)
            .toList();
        assertEquals(2, positionIds.size());
        assertTrue(positionIds.contains(firstPositionId));

        binance.market("98", "98", "100", "98", "99");
        service.processLiquidations(new BigDecimal("98"), NOW);
        service.processLiquidations(new BigDecimal("98"), NOW);

        for (UUID positionId : positionIds)
        {
            PaperTrade trade = repository.findTrade(positionId).orElseThrow();
            assertEquals(TradeStatus.CLOSED, trade.status());
            assertEquals(TradeCloseReason.LIQUIDATION, trade.closeReason());
            assertDecimal("-4500", trade.grossRealizedPnl());
            assertDecimal("88.2", trade.exitFee());
            assertDecimal("2756.25", trade.liquidationFee());
            assertDecimal("-7434.45", trade.realizedPnl());
        }
        assertDecimal("0", repository.getAccount(userId).balanceUsd());
        assertEquals(
            1L,
            jdbcClient.sql("""
                    SELECT COUNT(*)
                    FROM paper_liquidation_events
                    WHERE user_id = :userId
                    """)
                .param("userId", userId)
                .query(Long.class)
                .single()
        );
        assertDecimal(
            "100",
            jdbcClient.sql("""
                    SELECT last_price
                    FROM paper_liquidation_events
                    WHERE user_id = :userId
                    """)
                .param("userId", userId)
                .query(BigDecimal.class)
                .single()
        );
        assertDecimal(
            "98",
            jdbcClient.sql("""
                    SELECT mark_price
                    FROM paper_liquidation_events
                    WHERE user_id = :userId
                    """)
                .param("userId", userId)
                .query(BigDecimal.class)
                .single()
        );
        assertEquals(
            1L,
            jdbcClient.sql("""
                    SELECT COUNT(*)
                    FROM paper_account_ledger
                    WHERE user_id = :userId
                      AND event_type = 'INSURANCE_CREDIT'
                    """)
                .param("userId", userId)
                .query(Long.class)
                .single()
        );
    }

    @Test
    void newAccountsStartWithAnAuditableOpeningBalance()
    {
        UUID userId = newUser("18").getUniqueId();

        assertDecimal(
            "10000",
            jdbcClient.sql("""
                    SELECT amount_usd
                    FROM paper_account_ledger
                    WHERE user_id = :userId
                      AND event_type = 'ACCOUNT_OPENED'
                    """)
                .param("userId", userId)
                .query(BigDecimal.class)
                .single()
        );
        assertDecimal(
            "10000",
            jdbcClient.sql("""
                    SELECT balance_after_usd
                    FROM paper_account_ledger
                    WHERE user_id = :userId
                      AND event_type = 'ACCOUNT_OPENED'
                    """)
                .param("userId", userId)
                .query(BigDecimal.class)
                .single()
        );
    }

    @Test
    void markObservedBeforeAPositionOpenedCannotLiquidateIt()
    {
        UUID userId = newUser("19").getUniqueId();
        binance.quote("100", "100");
        UUID positionId = service.openPosition(
            userId,
            open(TradeSide.LONG, "9000", 50, null, null)
        ).openPositions().getFirst().id();
        binance.market("98", "98", "100", "98", "99");

        service.processLiquidations(
            new BigDecimal("98"),
            NOW.minusSeconds(1)
        );

        assertEquals(
            TradeStatus.OPEN,
            repository.findTrade(positionId).orElseThrow().status()
        );
    }

    @Test
    void fundingUsesSettlementMarkAndIsExactOnceForLongAndShort()
    {
        UUID userId = newUser("17").getUniqueId();
        binance.quote("100", "100");
        UUID longId = service.openPosition(
            userId,
            open(TradeSide.LONG, "100", 1, null, null)
        ).openPositions().getFirst().id();
        UUID shortId = service.openPosition(
            userId,
            open(TradeSide.SHORT, "100", 1, null, null)
        ).openPositions().stream()
            .filter(position -> position.side() == TradeSide.SHORT)
            .findFirst()
            .orElseThrow()
            .id();
        binance.market("200", "200", "200", "200", "200");
        fundingSource.respondWith(new BinanceFundingRate(
            "BTCUSDT",
            NOW,
            "FUNDING_RATE",
            new BigDecimal("0.01"),
            new BigDecimal("100")
        ));

        fundingService.reconcile();
        fundingService.reconcile();

        assertDecimal(
            "-1",
            repository.findTrade(longId).orElseThrow().fundingPnl()
        );
        assertDecimal(
            "1",
            repository.findTrade(shortId).orElseThrow().fundingPnl()
        );
        assertDecimal(
            "9999.92",
            repository.getAccount(userId).balanceUsd()
        );
        assertEquals(
            2L,
            jdbcClient.sql("""
                    SELECT COUNT(*)
                    FROM paper_trade_funding_settlements
                    WHERE user_id = :userId
                    """)
                .param("userId", userId)
                .query(Long.class)
                .single()
        );
    }

    private void openAfter(
        CountDownLatch start,
        UUID userId,
        String margin
    )
    {
        await(start);
        service.openPosition(
            userId,
            open(TradeSide.LONG, margin, 1, null, null)
        );
    }

    private void closeAfter(
        CountDownLatch start,
        UUID userId,
        UUID positionId
    )
    {
        await(start);
        service.closePosition(userId, positionId);
    }

    private static void await(CountDownLatch latch)
    {
        try
        {
            latch.await();
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private User newUser(String suffix)
    {
        return userService.findOrCreateByWalletAddress(
            "0x" + "0".repeat(38) + suffix
        );
    }

    private static OpenPositionRequest open(
        TradeSide side,
        String margin,
        int leverage,
        String stopLoss,
        String takeProfit
    )
    {
        return new OpenPositionRequest(
            UUID.randomUUID(),
            side,
            leverage,
            new BigDecimal(margin),
            stopLoss == null ? null : new BigDecimal(stopLoss),
            takeProfit == null ? null : new BigDecimal(takeProfit)
        );
    }

    private static PaperPositionResponse position(
        List<PaperPositionResponse> positions,
        UUID id
    )
    {
        return positions.stream()
            .filter(position -> position.id().equals(id))
            .findFirst()
            .orElseThrow();
    }

    private static Stream<Arguments> riskControlCases()
    {
        return Stream.of(
            Arguments.of(
                "05",
                TradeSide.LONG,
                "1000",
                "90",
                null,
                "90",
                "91",
                "90",
                TradeCloseReason.STOP_LOSS,
                "8992.4"
            ),
            Arguments.of(
                "06",
                TradeSide.LONG,
                "1000",
                null,
                "110",
                "110",
                "111",
                "110",
                TradeCloseReason.TAKE_PROFIT,
                "10991.6"
            ),
            Arguments.of(
                "07",
                TradeSide.SHORT,
                "990",
                "110",
                null,
                "109",
                "110",
                "110",
                TradeCloseReason.STOP_LOSS,
                "8891.64"
            ),
            Arguments.of(
                "08",
                TradeSide.SHORT,
                "990",
                null,
                "90",
                "89",
                "90",
                "90",
                TradeCloseReason.TAKE_PROFIT,
                "10892.44"
            )
        );
    }

    private static void assertDecimal(String expected, BigDecimal actual)
    {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PriceTestConfiguration
    {
        @Bean
        @Primary
        StubBinanceWrapper stubBinanceWrapper()
        {
            return new StubBinanceWrapper();
        }

        @Bean
        @Primary
        StubFundingRateSource stubFundingRateSource()
        {
            return new StubFundingRateSource();
        }

        @Bean
        @Primary
        Clock fixedTradingClock()
        {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    static final class StubBinanceWrapper extends BinanceWrapper
    {
        private final AtomicReference<BtcPerpetualMarketSnapshot> market =
            new AtomicReference<>(
                snapshot(BigDecimal.ONE, BigDecimal.ONE)
            );

        void quote(String bid, String ask)
        {
            market.set(snapshot(
                new BigDecimal(bid),
                new BigDecimal(ask)
            ));
        }

        void market(
            String bid,
            String ask,
            String last,
            String mark,
            String index
        )
        {
            market.set(new BtcPerpetualMarketSnapshot(
                new BigDecimal(last),
                NOW,
                new BigDecimal(bid),
                new BigDecimal(ask),
                NOW,
                new BigDecimal(mark),
                new BigDecimal(index),
                BigDecimal.ZERO,
                NOW.plusSeconds(3600),
                NOW
            ));
        }

        @Override
        public BtcQuote getBtcQuote()
        {
            return market.get().quote();
        }

        @Override
        public BtcPerpetualMarketSnapshot getBtcPerpetualMarketSnapshot()
        {
            return market.get();
        }

        private static BtcPerpetualMarketSnapshot snapshot(
            BigDecimal bid,
            BigDecimal ask
        )
        {
            BigDecimal fairPrice = bid.add(ask)
                .divide(BigDecimal.valueOf(2));
            return new BtcPerpetualMarketSnapshot(
                fairPrice,
                NOW,
                bid,
                ask,
                NOW,
                fairPrice,
                fairPrice,
                BigDecimal.ZERO,
                NOW.plusSeconds(3600),
                NOW
            );
        }
    }

    static final class StubFundingRateSource
        implements BinanceFundingRateSource
    {
        private final AtomicReference<List<BinanceFundingRate>> response =
            new AtomicReference<>(List.of());

        void respondWith(BinanceFundingRate fundingRate)
        {
            response.set(List.of(fundingRate));
        }

        @Override
        public List<BinanceFundingRate> getBtcFundingRates(
            Instant startTime,
            Instant endTime,
            int limit
        )
        {
            return response.get();
        }
    }
}
