package com.raretable.casino.paper_trading.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.raretable.casino.paper_trading.market_data.BtcCandleInterval;
import com.raretable.casino.paper_trading.market_data.CandleHistoryQuery;
import com.raretable.casino.paper_trading.price.BinanceWrapper;
import com.raretable.casino.security.ApiAccessDeniedHandler;
import com.raretable.casino.security.ApiAuthenticationEntryPoint;
import com.raretable.casino.security.SecurityConfig;
import com.raretable.casino.security.WalletPrincipal;
import com.raretable.casino.security.WalletSessionService;

@WebMvcTest(PaperTradingController.class)
@Import({
    SecurityConfig.class,
    ApiAuthenticationEntryPoint.class,
    ApiAccessDeniedHandler.class,
    PaperTradingControllerTests.TestConfig.class
})
@TestPropertySource(properties = "raretable.auth.session.absolute-timeout=PT8H")
class PaperTradingControllerTests
{
    private static final Instant NOW = Instant.parse("2026-07-27T09:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StubBinanceWrapper binanceWrapper;

    @Autowired
    private StubCandleHistory candleHistory;

    @Test
    void btcPriceRequiresAuthentication() throws Exception
    {
        mockMvc.perform(get("/api/paper-trading/btc-price"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void btcCandlesRequireAuthentication() throws Exception
    {
        mockMvc.perform(get("/api/paper-trading/btc-candles"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void marketDataWebSocketRequiresAuthentication() throws Exception
    {
        mockMvc.perform(get("/ws/market-data/btcusdt/1h"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void returnsBtcUsdtMidPrice() throws Exception
    {
        BigDecimal price = new BigDecimal("65432.12500000");
        binanceWrapper.price = price;
        int callsBeforeRequest = binanceWrapper.calls;

        mockMvc.perform(get("/api/paper-trading/btc-price")
                .session(authenticatedSession()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.symbol").value("BTCUSDT"))
            .andExpect(jsonPath("$.price").isNumber())
            .andExpect(jsonPath("$.price").value(65432.125));

        assertEquals(callsBeforeRequest + 1, binanceWrapper.calls);
    }

    @Test
    void returnsBtcUsdtOneHourCandles() throws Exception
    {
        candleHistory.response = new BtcCandlesResponse(
            "BTCUSDT",
            "USD_M_PERPETUAL",
            "4h",
            List.of(
                new BtcCandle(
                    1785139200,
                    new BigDecimal("65000.10"),
                    new BigDecimal("65500.20"),
                    new BigDecimal("64900.30"),
                    new BigDecimal("65300.40"),
                    new BigDecimal("123.45")
                ),
                new BtcCandle(
                    1785142800,
                    new BigDecimal("65300.40"),
                    new BigDecimal("65700.50"),
                    new BigDecimal("65200.60"),
                    new BigDecimal("65600.70"),
                    new BigDecimal("98.76")
                )
            ),
            true,
            1785139200L
        );

        mockMvc.perform(get("/api/paper-trading/btc-candles")
                .param("interval", "4h")
                .param("before", "1785146400")
                .param("limit", "1000")
                .session(authenticatedSession()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.symbol").value("BTCUSDT"))
            .andExpect(
                jsonPath("$.productType").value("USD_M_PERPETUAL")
            )
            .andExpect(jsonPath("$.interval").value("4h"))
            .andExpect(jsonPath("$.hasMore").value(true))
            .andExpect(jsonPath("$.nextBefore").value(1785139200L))
            .andExpect(jsonPath("$.candles.length()").value(2))
            .andExpect(jsonPath("$.candles[0].time").value(1785139200L))
            .andExpect(jsonPath("$.candles[0].open").value(65000.10))
            .andExpect(jsonPath("$.candles[0].high").value(65500.20))
            .andExpect(jsonPath("$.candles[0].low").value(64900.30))
            .andExpect(jsonPath("$.candles[0].close").value(65300.40))
            .andExpect(jsonPath("$.candles[0].volume").value(123.45))
            .andExpect(jsonPath("$.candles[1].time").value(1785142800L));

        assertEquals(BtcCandleInterval.FOUR_HOURS, candleHistory.interval);
        assertEquals(1785146400L, candleHistory.before);
        assertEquals(1000, candleHistory.limit);
    }

    @Test
    void defaultsToOneHourAndRejectsUnsupportedIntervals() throws Exception
    {
        candleHistory.response = new BtcCandlesResponse(
            "BTCUSDT",
            "USD_M_PERPETUAL",
            "1h",
            List.of(),
            false,
            null
        );

        mockMvc.perform(get("/api/paper-trading/btc-candles")
                .session(authenticatedSession()))
            .andExpect(status().isOk());
        assertEquals(BtcCandleInterval.ONE_HOUR, candleHistory.interval);

        int callsBeforeInvalidRequest = candleHistory.calls;
        mockMvc.perform(get("/api/paper-trading/btc-candles")
                .param("interval", "30m")
                .session(authenticatedSession()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertEquals(callsBeforeInvalidRequest, candleHistory.calls);
    }

    @Test
    void rejectsInvalidCandlePaginationParameters() throws Exception
    {
        int callsBeforeRequest = candleHistory.calls;

        mockMvc.perform(get("/api/paper-trading/btc-candles")
                .param("before", "0")
                .param("limit", "2001")
                .session(authenticatedSession()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/paper-trading/btc-candles")
                .param("before", "not-a-number")
                .session(authenticatedSession()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(jsonPath("$.message").value(
                "Request parameter has an invalid type"
            ));

        assertEquals(callsBeforeRequest, candleHistory.calls);
    }

    private static MockHttpSession authenticatedSession()
    {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                new WalletPrincipal(UUID.fromString(
                    "b6a074d3-f68d-4b65-88d8-f232ac1ed453"
                )),
                null,
                List.of()
            )
        );

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
            context
        );
        session.setAttribute(
            WalletSessionService.class.getName() + ".authenticatedAt",
            NOW
        );
        return session;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig
    {
        @Bean
        StubBinanceWrapper binanceWrapper()
        {
            return new StubBinanceWrapper();
        }

        @Bean
        StubCandleHistory candleHistory()
        {
            return new StubCandleHistory();
        }

        @Bean
        Clock clock()
        {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    static final class StubBinanceWrapper extends BinanceWrapper
    {
        private BigDecimal price;
        private int calls;

        @Override
        public BigDecimal getBtcPrice()
        {
            calls++;
            return price;
        }
    }

    static final class StubCandleHistory implements CandleHistoryQuery
    {
        private BtcCandlesResponse response;
        private BtcCandleInterval interval;
        private Long before;
        private Integer limit;
        private int calls;

        @Override
        public BtcCandlesResponse getBtcCandles(
            BtcCandleInterval requestedInterval,
            Long requestedBefore,
            Integer requestedLimit
        )
        {
            calls++;
            interval = requestedInterval;
            before = requestedBefore;
            limit = requestedLimit;
            return response;
        }
    }
}
