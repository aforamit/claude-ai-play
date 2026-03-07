package com.accounting.qbo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * QuickBooks Online configuration properties.
 * Set via environment variables or application.yml.
 *
 * Required: QBO_CLIENT_ID, QBO_CLIENT_SECRET
 * Optional: QBO_REDIRECT_URI, QBO_REALM_ID, QBO_SANDBOX
 */
@ConfigurationProperties(prefix = "qbo")
public class QboProperties {

    private String clientId;
    private String clientSecret;
    private String redirectUri = "http://localhost:8080/api/auth/callback";
    private String realmId;
    private boolean sandbox = true;
    private List<String> scopes = List.of("com.intuit.quickbooks.accounting");

    // ── OAuth Endpoint Constants ─────────────────────────────────────────────

    public static final String INTUIT_AUTH_URL =
            "https://appcenter.intuit.com/connect/oauth2";
    public static final String INTUIT_TOKEN_URL =
            "https://oauth.platform.intuit.com/oauth2/v1/tokens/bearer";
    public static final String INTUIT_REVOKE_URL =
            "https://developer.api.intuit.com/v2/oauth2/tokens/revoke";

    // ── QBO API Base URLs ────────────────────────────────────────────────────

    public String getBaseUrl() {
        return sandbox
                ? "https://sandbox-quickbooks.api.intuit.com"
                : "https://quickbooks.api.intuit.com";
    }

    public String getApiVersion() {
        return "v3";
    }

    public String getMinorVersion() {
        return "65";
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }

    public String getRealmId() { return realmId; }
    public void setRealmId(String realmId) { this.realmId = realmId; }

    public boolean isSandbox() { return sandbox; }
    public void setSandbox(boolean sandbox) { this.sandbox = sandbox; }

    public List<String> getScopes() { return scopes; }
    public void setScopes(List<String> scopes) { this.scopes = scopes; }

    public String getScopesAsString() {
        return String.join(" ", scopes);
    }
}
