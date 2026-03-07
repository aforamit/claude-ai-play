package com.accounting.qbo.auth;

import com.accounting.qbo.config.QboProperties;
import com.accounting.qbo.exception.QboException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages the QuickBooks Online OAuth 2.0 Authorization Code Flow.
 *
 * <p>Flow:
 * <ol>
 *   <li>Build authorization URL → redirect user to Intuit login</li>
 *   <li>Receive callback with code → exchange for tokens</li>
 *   <li>Store tokens in {@link TokenStore}</li>
 *   <li>Auto-refresh expired access tokens on each API call</li>
 * </ol>
 */
@Service
public class QboOAuthService {

    private static final Logger log = LoggerFactory.getLogger(QboOAuthService.class);

    private final QboProperties props;
    private final TokenStore tokenStore;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public QboOAuthService(QboProperties props, TokenStore tokenStore,
                           RestClient qboRestClient, ObjectMapper objectMapper) {
        this.props = props;
        this.tokenStore = tokenStore;
        this.restClient = qboRestClient;
        this.objectMapper = objectMapper;
    }

    // ── Authorization URL ────────────────────────────────────────────────────

    /**
     * Builds the Intuit authorization URL to redirect the user to.
     * The state parameter prevents CSRF attacks.
     */
    public String buildAuthorizationUrl(String state) {
        return QboProperties.INTUIT_AUTH_URL +
                "?client_id=" + props.getClientId() +
                "&redirect_uri=" + encode(props.getRedirectUri()) +
                "&response_type=code" +
                "&scope=" + encode(props.getScopesAsString()) +
                "&state=" + state;
    }

    public String generateState() {
        return UUID.randomUUID().toString();
    }

    // ── Token Exchange ───────────────────────────────────────────────────────

    /**
     * Exchanges the authorization code for access + refresh tokens.
     * Called from the OAuth callback endpoint.
     */
    public OAuthToken exchangeCodeForToken(String code, String realmId) {
        log.info("Exchanging authorization code for tokens (realmId={})", realmId);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", props.getRedirectUri());

        OAuthToken token = requestToken(body, realmId);
        tokenStore.save(realmId, token);
        log.info("Tokens stored for realmId={}", realmId);
        return token;
    }

    // ── Token Refresh ────────────────────────────────────────────────────────

    /**
     * Refreshes the access token using the refresh token.
     * Called automatically by {@link #getValidToken(String)}.
     */
    public OAuthToken refreshToken(String realmId) {
        OAuthToken existing = tokenStore.get(realmId)
                .orElseThrow(() -> new QboException("No token found for realmId: " + realmId));

        if (existing.isRefreshTokenExpired()) {
            tokenStore.delete(realmId);
            throw new QboException("Refresh token expired for realmId: " + realmId +
                    ". Re-authorization required.");
        }

        log.info("Refreshing access token for realmId={}", realmId);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", existing.refreshToken());

        OAuthToken refreshed = requestToken(body, realmId);
        tokenStore.save(realmId, refreshed);
        return refreshed;
    }

    /**
     * Returns a valid (non-expired) access token, refreshing if necessary.
     * This is the primary method used by {@link com.accounting.qbo.client.QboApiClient}.
     */
    public OAuthToken getValidToken(String realmId) {
        OAuthToken token = tokenStore.get(realmId)
                .orElseThrow(() -> new QboException(
                        "Not authorized for realmId: " + realmId +
                        ". Visit /api/auth/connect to authorize."));

        if (token.isAccessTokenExpired()) {
            log.debug("Access token expired, refreshing for realmId={}", realmId);
            return refreshToken(realmId);
        }
        return token;
    }

    // ── Token Revocation ─────────────────────────────────────────────────────

    public void revokeToken(String realmId) {
        tokenStore.get(realmId).ifPresent(token -> {
            try {
                MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
                body.add("token", token.refreshToken());

                restClient.post()
                        .uri(QboProperties.INTUIT_REVOKE_URL)
                        .header("Authorization", basicAuth())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(body)
                        .retrieve()
                        .toBodilessEntity();

                log.info("Token revoked for realmId={}", realmId);
            } catch (Exception e) {
                log.warn("Failed to revoke token remotely: {}", e.getMessage());
            }
            tokenStore.delete(realmId);
        });
    }

    // ── Status ───────────────────────────────────────────────────────────────

    public Optional<OAuthToken> getTokenStatus(String realmId) {
        return tokenStore.get(realmId);
    }

    public boolean isAuthorized(String realmId) {
        return tokenStore.exists(realmId);
    }

    // ── Internal Helpers ─────────────────────────────────────────────────────

    private OAuthToken requestToken(MultiValueMap<String, String> body, String realmId) {
        try {
            String responseBody = restClient.post()
                    .uri(QboProperties.INTUIT_TOKEN_URL)
                    .header("Authorization", basicAuth())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode json = objectMapper.readTree(responseBody);
            return OAuthToken.of(
                    json.get("access_token").asText(),
                    json.get("refresh_token").asText(),
                    json.path("token_type").asText("Bearer"),
                    json.path("expires_in").asLong(3600),
                    realmId
            );
        } catch (Exception e) {
            throw new QboException("Failed to obtain token from Intuit: " + e.getMessage(), e);
        }
    }

    private String basicAuth() {
        String credentials = props.getClientId() + ":" + props.getClientSecret();
        return "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
