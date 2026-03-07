package com.accounting.qbo.auth;

import java.time.Instant;

/**
 * Immutable value object representing a QBO OAuth 2.0 token pair.
 * Access tokens expire in 1 hour; refresh tokens expire in 100 days.
 */
public record OAuthToken(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        Instant issuedAt,
        String realmId
) {
    /**
     * Returns true if the access token is expired or will expire within 60 seconds.
     */
    public boolean isAccessTokenExpired() {
        return Instant.now().isAfter(issuedAt.plusSeconds(expiresIn - 60));
    }

    /**
     * Returns true if the refresh token is expired (100 days).
     */
    public boolean isRefreshTokenExpired() {
        return Instant.now().isAfter(issuedAt.plusSeconds(8_640_000L - 300));
    }

    /** Convenience factory from an OAuth JSON response. */
    public static OAuthToken of(String accessToken, String refreshToken,
                                 String tokenType, long expiresIn, String realmId) {
        return new OAuthToken(accessToken, refreshToken, tokenType,
                expiresIn, Instant.now(), realmId);
    }
}
