    package com.raretable.casino.paper_trading.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.raretable.casino.paper_trading.trading.PaperTradingOperations;
import com.raretable.casino.paper_trading.trading.AccountRiskState;
import com.raretable.casino.paper_trading.trading.TradeSide;
import com.raretable.casino.security.ApiAccessDeniedHandler;
import com.raretable.casino.security.ApiAuthenticationEntryPoint;
import com.raretable.casino.security.SecurityConfig;
import com.raretable.casino.security.WalletPrincipal;
import com.raretable.casino.security.WalletSessionService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@WebMvcTest(PaperTradingPositionsController.class)
@Import({
    SecurityConfig.class,
    ApiAuthenticationEntryPoint.class,
    ApiAccessDeniedHandler.class,
    PaperTradingPositionsControllerTests.TestConfig.class
})
@TestPropertySource(properties = "raretable.auth.session.absolute-timeout=PT8H")
class PaperTradingPositionsControllerTests
{
    private static final UUID USER_ID = UUID.fromString(
        "3ba0837d-484b-4bb6-b948-b543e0ddf42e"
    );
    private static final UUID POSITION_ID = UUID.fromString(
        "b67ae6dd-d704-4147-8fb3-f0e8668de8fc"
    );
    private static final Instant NOW =
        Instant.parse("2026-07-27T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private StubPaperTradingOperations tradingService;

    @BeforeEach
    void resetTradingService()
    {
        tradingService.reset();
    }

    @Test
    void portfolioRequiresAuthentication() throws Exception
    {
        mockMvc.perform(get("/api/paper-trading/portfolio"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        assertEquals(0, tradingService.calls);
    }

    @Test
    void portfolioUsesAuthenticatedUserAndRequestedHistoryLimit()
        throws Exception
    {
        tradingService.response = portfolio();

        mockMvc.perform(get("/api/paper-trading/portfolio")
                .param("closedTradeLimit", "25")
                .session(authenticatedSession()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
            .andExpect(jsonPath("$.quote.symbol").value("BTCUSDT"))
            .andExpect(jsonPath("$.account.balance").value(10000))
            .andExpect(jsonPath("$.openPositions.length()").value(0))
            .andExpect(jsonPath("$.closedTrades.length()").value(0));

        assertEquals(USER_ID, tradingService.userId);
        assertEquals(25, tradingService.closedTradeLimit);
    }

    @Test
    void stateChangingOrdersRequireCsrf() throws Exception
    {
        mockMvc.perform(post("/api/paper-trading/positions")
                .session(authenticatedSession())
                .header(
                    PaperTradingPositionsController.EXPECTED_USER_HEADER,
                    USER_ID
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(validOrder()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertEquals(0, tradingService.calls);
    }

    @Test
    void rejectsAnOrderWhenTheDisplayedWalletDiffersFromTheSession()
        throws Exception
    {
        MockHttpSession session = authenticatedSession();
        CsrfHeader csrf = csrf(session);

        mockMvc.perform(post("/api/paper-trading/positions")
                .session(session)
                .header(csrf.name(), csrf.value())
                .header(
                    PaperTradingPositionsController.EXPECTED_USER_HEADER,
                    UUID.fromString(
                        "5d42fc56-fb0a-4e96-8bab-a9de3fc3d15d"
                    )
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(validOrder()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SESSION_USER_MISMATCH"));

        assertEquals(0, tradingService.calls);
    }

    @Test
    void opensUpdatesAndClosesForAuthenticatedUser() throws Exception
    {
        MockHttpSession session = authenticatedSession();
        CsrfHeader csrf = csrf(session);
        PaperTradingPortfolioResponse portfolio = portfolio();
        tradingService.response = portfolio;

        mockMvc.perform(post("/api/paper-trading/positions")
                .session(session)
                .header(csrf.name(), csrf.value())
                .header(
                    PaperTradingPositionsController.EXPECTED_USER_HEADER,
                    USER_ID
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(validOrder()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.account.balance").value(10000));

        mockMvc.perform(patch(
                "/api/paper-trading/positions/{positionId}/risk-controls",
                POSITION_ID
            )
                .session(session)
                .header(csrf.name(), csrf.value())
                .header(
                    PaperTradingPositionsController.EXPECTED_USER_HEADER,
                    USER_ID
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "stopLoss": 91,
                      "takeProfit": 111
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(post(
                "/api/paper-trading/positions/{positionId}/close",
                POSITION_ID
            )
                .session(session)
                .header(csrf.name(), csrf.value())
                .header(
                    PaperTradingPositionsController.EXPECTED_USER_HEADER,
                    USER_ID
                ))
            .andExpect(status().isOk());

        OpenPositionRequest openRequest = tradingService.openRequest;
        assertEquals(TradeSide.LONG, openRequest.side());
        assertEquals(
            UUID.fromString("62415a7a-99cc-41f1-9339-c17d2a46da13"),
            openRequest.clientOrderId()
        );
        assertEquals(10, openRequest.leverage());
        assertEquals(new BigDecimal("1000"), openRequest.marginUsd());
        assertEquals(new BigDecimal("90"), openRequest.stopLoss());
        assertEquals(new BigDecimal("110"), openRequest.takeProfit());

        UpdateRiskControlsRequest riskRequest = tradingService.riskRequest;
        assertEquals(
            new BigDecimal("91"),
            riskRequest.stopLoss()
        );
        assertEquals(
            new BigDecimal("111"),
            riskRequest.takeProfit()
        );
        assertEquals(USER_ID, tradingService.userId);
        assertEquals(POSITION_ID, tradingService.positionId);
        assertEquals(3, tradingService.calls);
    }

    @Test
    void previewsTheExactAuthenticatedOrderIntent() throws Exception
    {
        MockHttpSession session = authenticatedSession();
        CsrfHeader csrf = csrf(session);

        mockMvc.perform(post("/api/paper-trading/positions/preview")
                .session(session)
                .header(csrf.name(), csrf.value())
                .header(
                    PaperTradingPositionsController.EXPECTED_USER_HEADER,
                    USER_ID
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "side": "SHORT",
                      "leverage": 25,
                      "marginUsd": 400,
                      "stopLoss": 110,
                      "takeProfit": 90
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entryFee").value(0.4))
            .andExpect(jsonPath("$.lowerBankruptcyPrice").value(1))
            .andExpect(
                jsonPath("$.estimatedLowerLiquidationPrice").value(5)
            )
            .andExpect(jsonPath("$.takerFeeRate").value(0.0004))
            .andExpect(jsonPath("$.liquidationFeeRate").value(0.0125))
            .andExpect(jsonPath("$.liquidationMode").value("FULL"))
            .andExpect(
                jsonPath("$.negativeBalancePolicy").value("FLOOR_ZERO")
            )
            .andExpect(jsonPath("$.stopTriggerPriceType").value("LAST"))
            .andExpect(
                jsonPath("$.ruleVersion")
                    .value("RARETABLE_BTCUSDT_V1")
            );

        assertEquals(USER_ID, tradingService.userId);
        assertEquals(TradeSide.SHORT, tradingService.previewRequest.side());
        assertEquals(25, tradingService.previewRequest.leverage());
        assertEquals(
            new BigDecimal("400"),
            tradingService.previewRequest.marginUsd()
        );
    }

    @Test
    void rejectsOrdersOutsideTheSupportedLeverageRange() throws Exception
    {
        MockHttpSession session = authenticatedSession();
        CsrfHeader csrf = csrf(session);

        mockMvc.perform(post("/api/paper-trading/positions")
                .session(session)
                .header(csrf.name(), csrf.value())
                .header(
                    PaperTradingPositionsController.EXPECTED_USER_HEADER,
                    USER_ID
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "side": "LONG",
                      "leverage": 101,
                      "marginUsd": 1000
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertEquals(0, tradingService.calls);
    }

    private CsrfHeader csrf(MockHttpSession session) throws Exception
    {
        MvcResult result = mockMvc.perform(get("/api/test/csrf")
                .session(session))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode csrf = jsonMapper.readTree(
            result.getResponse().getContentAsString()
        );
        return new CsrfHeader(
            csrf.get("headerName").stringValue(),
            csrf.get("token").stringValue()
        );
    }

    private static MockHttpSession authenticatedSession()
    {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                new WalletPrincipal(USER_ID),
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

    private static PaperTradingPortfolioResponse portfolio()
    {
        BigDecimal zero = new BigDecimal("0.00000000");
        BigDecimal balance = new BigDecimal("10000.00000000");
        return new PaperTradingPortfolioResponse(
            USER_ID,
            new TradingQuoteResponse(
                "BTCUSDT",
                "USD_M_PERPETUAL",
                new BigDecimal("99.00000000"),
                new BigDecimal("100.00000000"),
                new BigDecimal("99.50000000"),
                new BigDecimal("99.60000000"),
                new BigDecimal("99.55000000"),
                new BigDecimal("0.00010000"),
                NOW.plusSeconds(3600),
                NOW,
                NOW,
                NOW
            ),
            new TradingAccountResponse(
                balance,
                balance,
                balance,
                zero,
                zero,
                zero,
                zero,
                balance,
                null,
                AccountRiskState.NO_POSITIONS,
                null,
                null
            ),
            List.of(),
            List.of()
        );
    }

    private static PaperPositionPreviewResponse preview()
    {
        return new PaperPositionPreviewResponse(
            new BigDecimal("100"),
            new BigDecimal("1000"),
            new BigDecimal("10"),
            new BigDecimal("0.4"),
            new BigDecimal("0.4"),
            new BigDecimal("100.08"),
            new BigDecimal("1"),
            new BigDecimal("5"),
            new BigDecimal("1"),
            null,
            new BigDecimal("5"),
            null,
            new BigDecimal("4"),
            new BigDecimal("0.004"),
            new BigDecimal("0.04"),
            new BigDecimal("8999.2"),
            new BigDecimal("8999"),
            new BigDecimal("0.0004"),
            new BigDecimal("0.0125"),
            "FULL",
            "FLOOR_ZERO",
            "LAST",
            "RARETABLE_BTCUSDT_V1",
            NOW
        );
    }

    private static String validOrder()
    {
        return """
            {
              "clientOrderId": "62415a7a-99cc-41f1-9339-c17d2a46da13",
              "side": "LONG",
              "leverage": 10,
              "marginUsd": 1000,
              "stopLoss": 90,
              "takeProfit": 110
            }
            """;
    }

    private record CsrfHeader(String name, String value)
    {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig
    {
        @Bean
        Clock clock()
        {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        StubPaperTradingOperations tradingService()
        {
            return new StubPaperTradingOperations();
        }

        @Bean
        TestCsrfController testCsrfController()
        {
            return new TestCsrfController();
        }
    }

    @RestController
    static final class TestCsrfController
    {
        @GetMapping("/api/test/csrf")
        CsrfToken csrf(CsrfToken csrfToken)
        {
            return csrfToken;
        }
    }

    static final class StubPaperTradingOperations
        implements PaperTradingOperations
    {
        private PaperTradingPortfolioResponse response;
        private UUID userId;
        private UUID positionId;
        private int closedTradeLimit;
        private OpenPositionRequest openRequest;
        private PreviewPositionRequest previewRequest;
        private UpdateRiskControlsRequest riskRequest;
        private int calls;

        void reset()
        {
            response = null;
            userId = null;
            positionId = null;
            closedTradeLimit = 0;
            openRequest = null;
            previewRequest = null;
            riskRequest = null;
            calls = 0;
        }

        @Override
        public PaperTradingPortfolioResponse getPortfolio(
            UUID requestedUserId,
            int requestedClosedTradeLimit
        )
        {
            calls++;
            userId = requestedUserId;
            closedTradeLimit = requestedClosedTradeLimit;
            return response;
        }

        @Override
        public PaperTradingPortfolioResponse openPosition(
            UUID requestedUserId,
            OpenPositionRequest request
        )
        {
            calls++;
            userId = requestedUserId;
            openRequest = request;
            return response;
        }

        @Override
        public PaperPositionPreviewResponse previewPosition(
            UUID requestedUserId,
            PreviewPositionRequest request
        )
        {
            calls++;
            userId = requestedUserId;
            previewRequest = request;
            return preview();
        }

        @Override
        public PaperTradingPortfolioResponse updateRiskControls(
            UUID requestedUserId,
            UUID requestedPositionId,
            UpdateRiskControlsRequest request
        )
        {
            calls++;
            userId = requestedUserId;
            positionId = requestedPositionId;
            riskRequest = request;
            return response;
        }

        @Override
        public PaperTradingPortfolioResponse closePosition(
            UUID requestedUserId,
            UUID requestedPositionId
        )
        {
            calls++;
            userId = requestedUserId;
            positionId = requestedPositionId;
            return response;
        }

        @Override
        public void processRiskControls(
            BigDecimal observedTradePrice,
            Instant observedAt
        )
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void processLiquidations(
            BigDecimal observedMarkPrice,
            Instant observedAt
        )
        {
            throw new UnsupportedOperationException();
        }
    }
}
