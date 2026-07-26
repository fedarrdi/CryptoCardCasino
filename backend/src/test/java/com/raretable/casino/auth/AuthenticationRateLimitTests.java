package com.raretable.casino.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigInteger;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import com.raretable.casino.InfrastructureTestConfiguration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {
    "raretable.auth.rate-limit.challenge.requests-per-source=2",
    "raretable.auth.rate-limit.challenge.requests-per-wallet=100",
    "raretable.auth.rate-limit.challenge.requests-global=1000",
    "raretable.auth.rate-limit.verification.requests-per-source=1",
    "raretable.auth.rate-limit.verification.requests-per-wallet=1",
    "raretable.auth.rate-limit.verification.requests-global=1000"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(InfrastructureTestConfiguration.class)
class AuthenticationRateLimitTests
{
    private static final String WALLET_ADDRESS = Keys.toChecksumAddress(
        Numeric.prependHexPrefix(
            Keys.getAddress(ECKeyPair.create(BigInteger.valueOf(30)).getPublicKey())
        )
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void challengeRateLimitReturnsRetryAfter() throws Exception
    {
        String source = "203.0.113.10";

        requestChallenge(source);
        requestChallenge(source);

        mockMvc.perform(post("/api/auth/challenges")
                .with(request ->
                {
                    request.setRemoteAddr(source);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(challengeRequestBody()))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void verificationSourceRateLimitRejectsRepeatedRequests() throws Exception
    {
        String source = "203.0.113.20";
        String requestBody = verificationRequestBody("missing01");

        mockMvc.perform(post("/api/auth/sessions")
                .with(request ->
                {
                    request.setRemoteAddr(source);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/sessions")
                .with(request ->
                {
                    request.setRemoteAddr(source);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void verificationWalletLimitIsSharedAcrossSources() throws Exception
    {
        String firstNonce = requestChallenge("203.0.113.30");

        verifyWithInvalidSignature(firstNonce, "203.0.113.31", 401);

        String secondNonce = requestChallenge("203.0.113.32");

        verifyWithInvalidSignature(secondNonce, "203.0.113.33", 429);
    }

    private String requestChallenge(String source) throws Exception
    {
        MvcResult result = mockMvc.perform(post("/api/auth/challenges")
                .with(request ->
                {
                    request.setRemoteAddr(source);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(challengeRequestBody()))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode response = jsonMapper.readTree(result.getResponse().getContentAsString());
        return response.get("nonce").stringValue();
    }

    private void verifyWithInvalidSignature(
        String nonce,
        String source,
        int expectedStatus
    ) throws Exception
    {
        mockMvc.perform(post("/api/auth/sessions")
                .with(request ->
                {
                    request.setRemoteAddr(source);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(verificationRequestBody(nonce)))
            .andExpect(status().is(expectedStatus));
    }

    private String challengeRequestBody() throws Exception
    {
        return jsonMapper.writeValueAsString(Map.of(
            "walletAddress", WALLET_ADDRESS,
            "chainId", 1
        ));
    }

    private String verificationRequestBody(String nonce) throws Exception
    {
        return jsonMapper.writeValueAsString(Map.of(
            "nonce", nonce,
            "signature", "invalid"
        ));
    }
}
