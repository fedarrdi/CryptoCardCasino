package com.raretable.casino.paper_trading;

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
import org.springframework.web.client.RestClient;

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

    @Test
    void btcPriceRequiresAuthentication() throws Exception
    {
        mockMvc.perform(get("/api/paper-trading/btc-price"))
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
        Clock clock()
        {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    static final class StubBinanceWrapper extends BinanceWrapper
    {
        private BigDecimal price;
        private int calls;

        StubBinanceWrapper()
        {
            super(RestClient.create());
        }

        @Override
        public BigDecimal getBtcPrice()
        {
            calls++;
            return price;
        }
    }
}
