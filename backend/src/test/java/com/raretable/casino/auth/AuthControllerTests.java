package com.raretable.casino.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.ActiveProfiles;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import com.raretable.casino.PostgresTestConfiguration;
import com.raretable.casino.table.Table;
import com.raretable.casino.table.TableService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class AuthControllerTests
{
    private static final ECKeyPair WALLET = ECKeyPair.create(BigInteger.TEN);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private TableService tableService;

    @Test
    void verifiedWalletCreatesAuthenticatedSession() throws Exception
    {
        LoginResult login = login();

        mockMvc.perform(get("/api/auth/me").session(login.session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(login.userId()))
            .andExpect(jsonPath("$.walletAddress").value(getAddress(WALLET)));
    }

    @Test
    void currentUserRequiresAuthentication() throws Exception
    {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void gameApisRequireAuthentication() throws Exception
    {
        mockMvc.perform(get("/api/tables"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void csrfTokenRequiresAuthenticationWithoutCreatingSession() throws Exception
    {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
            .andReturn();

        assertNull(result.getRequest().getSession(false));
        assertNull(result.getResponse().getCookie("JSESSIONID"));
    }

    @Test
    void authenticatedPrincipalCreatesTable() throws Exception
    {
        LoginResult login = login();
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf").session(login.session()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
            .andReturn();
        JsonNode csrfResponse = jsonMapper.readTree(
            csrfResult.getResponse().getContentAsString()
        );
        String requestBody = jsonMapper.writeValueAsString(Map.of(
            "gameType", "CHEAT",
            "playersToStart", 2
        ));
        MvcResult result = mockMvc.perform(post("/api/tables")
                .session(login.session())
                .header(
                    csrfResponse.get("headerName").stringValue(),
                    csrfResponse.get("token").stringValue()
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andReturn();
        JsonNode response = jsonMapper.readTree(result.getResponse().getContentAsString());
        Table table = tableService.getTable(UUID.fromString(
            response.get("tableId").stringValue()
        ));

        assertEquals(
            UUID.fromString(login.userId()),
            table.getUsers().get(0).getUniqueId()
        );
    }

    @Test
    void signatureCannotCreateTwoSessionsFromOneChallenge() throws Exception
    {
        ChallengeResponse challenge = requestChallenge();
        String signature = sign(challenge.message(), WALLET);
        String requestBody = jsonMapper.writeValueAsString(Map.of(
            "nonce", challenge.nonce(),
            "signature", signature
        ));

        mockMvc.perform(post("/api/auth/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void logoutRequiresCsrfProtection() throws Exception
    {
        LoginResult login = login();

        mockMvc.perform(post("/api/auth/logout").session(login.session()))
            .andExpect(status().isForbidden());

        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf").session(login.session()))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode csrfResponse = jsonMapper.readTree(
            csrfResult.getResponse().getContentAsString()
        );

        mockMvc.perform(post("/api/auth/logout")
                .session(login.session())
                .header(
                    csrfResponse.get("headerName").stringValue(),
                    csrfResponse.get("token").stringValue()
                ))
            .andExpect(status().isNoContent());
    }

    private LoginResult login() throws Exception
    {
        ChallengeResponse challenge = requestChallenge();
        String requestBody = jsonMapper.writeValueAsString(Map.of(
            "nonce", challenge.nonce(),
            "signature", sign(challenge.message(), WALLET)
        ));
        MvcResult result = mockMvc.perform(post("/api/auth/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.walletAddress").value(getAddress(WALLET)))
            .andReturn();
        JsonNode response = jsonMapper.readTree(result.getResponse().getContentAsString());

        return new LoginResult(
            response.get("userId").stringValue(),
            (MockHttpSession) result.getRequest().getSession(false)
        );
    }

    private ChallengeResponse requestChallenge() throws Exception
    {
        String requestBody = jsonMapper.writeValueAsString(Map.of(
            "walletAddress", getAddress(WALLET),
            "chainId", 1
        ));
        MvcResult result = mockMvc.perform(post("/api/auth/challenges")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode response = jsonMapper.readTree(result.getResponse().getContentAsString());

        return new ChallengeResponse(
            response.get("nonce").stringValue(),
            response.get("message").stringValue(),
            Instant.parse(response.get("expiresAt").stringValue())
        );
    }

    private static String getAddress(ECKeyPair wallet)
    {
        return Keys.toChecksumAddress(
            Numeric.prependHexPrefix(Keys.getAddress(wallet.getPublicKey()))
        );
    }

    private static String sign(String message, ECKeyPair wallet)
    {
        Sign.SignatureData signatureData = Sign.signPrefixedMessage(
            message.getBytes(StandardCharsets.UTF_8),
            wallet
        );
        byte[] signature = new byte[65];

        System.arraycopy(signatureData.getR(), 0, signature, 0, 32);
        System.arraycopy(signatureData.getS(), 0, signature, 32, 32);
        System.arraycopy(signatureData.getV(), 0, signature, 64, 1);

        return Numeric.toHexString(signature);
    }

    private record LoginResult(String userId, MockHttpSession session)
    {
    }
}
