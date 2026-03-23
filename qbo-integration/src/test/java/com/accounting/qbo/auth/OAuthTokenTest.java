package com.accounting.qbo.auth;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthTokenTest {

    // ── isAccessTokenExpired ──────────────────────────────────────────────────

    @Test
    void accessToken_freshToken_notExpired() {
        OAuthToken token = new OAuthToken("access", "refresh", "Bearer", 3600,
                Instant.now(), "realm1");
        assertThat(token.isAccessTokenExpired()).isFalse();
    }

    @Test
    void accessToken_issuedMoreThanExpiresInSecondsAgo_isExpired() {
        // issued 3600+ seconds ago → expired
        Instant old = Instant.now().minusSeconds(3700);
        OAuthToken token = new OAuthToken("access", "refresh", "Bearer", 3600, old, "realm1");
        assertThat(token.isAccessTokenExpired()).isTrue();
    }

    @Test
    void accessToken_withinSixtySecondBuffer_isExpired() {
        // issued 3545 seconds ago (expiresIn 3600 - 55s remaining < 60s buffer)
        Instant borderline = Instant.now().minusSeconds(3545);
        OAuthToken token = new OAuthToken("access", "refresh", "Bearer", 3600, borderline, "realm1");
        assertThat(token.isAccessTokenExpired()).isTrue();
    }

    @Test
    void accessToken_moreThanSixtySecondsRemaining_notExpired() {
        // issued 3400 seconds ago → 200 seconds left → not expired
        Instant recent = Instant.now().minusSeconds(3400);
        OAuthToken token = new OAuthToken("access", "refresh", "Bearer", 3600, recent, "realm1");
        assertThat(token.isAccessTokenExpired()).isFalse();
    }

    // ── isRefreshTokenExpired ─────────────────────────────────────────────────

    @Test
    void refreshToken_freshToken_notExpired() {
        OAuthToken token = new OAuthToken("access", "refresh", "Bearer", 3600,
                Instant.now(), "realm1");
        assertThat(token.isRefreshTokenExpired()).isFalse();
    }

    @Test
    void refreshToken_issuedMoreThan100DaysAgo_isExpired() {
        // 100 days = 8_640_000 seconds; buffer 300s → expired after 8_639_700s
        Instant old = Instant.now().minusSeconds(8_640_100L);
        OAuthToken token = new OAuthToken("access", "refresh", "Bearer", 3600, old, "realm1");
        assertThat(token.isRefreshTokenExpired()).isTrue();
    }

    @Test
    void refreshToken_issuedYesterday_notExpired() {
        Instant yesterday = Instant.now().minusSeconds(86_400);
        OAuthToken token = new OAuthToken("access", "refresh", "Bearer", 3600, yesterday, "realm1");
        assertThat(token.isRefreshTokenExpired()).isFalse();
    }

    // ── Factory method ────────────────────────────────────────────────────────

    @Test
    void of_setsIssuedAtToNow() {
        Instant before = Instant.now().minusSeconds(1);
        OAuthToken token = OAuthToken.of("access", "refresh", "Bearer", 3600, "realm1");
        Instant after = Instant.now().plusSeconds(1);

        assertThat(token.issuedAt()).isAfter(before).isBefore(after);
    }

    @Test
    void of_setsAllFieldsCorrectly() {
        OAuthToken token = OAuthToken.of("myAccess", "myRefresh", "Bearer", 7200, "realm42");

        assertThat(token.accessToken()).isEqualTo("myAccess");
        assertThat(token.refreshToken()).isEqualTo("myRefresh");
        assertThat(token.tokenType()).isEqualTo("Bearer");
        assertThat(token.expiresIn()).isEqualTo(7200);
        assertThat(token.realmId()).isEqualTo("realm42");
    }

    @Test
    void of_freshToken_isNotExpired() {
        OAuthToken token = OAuthToken.of("a", "r", "Bearer", 3600, "realm1");
        assertThat(token.isAccessTokenExpired()).isFalse();
        assertThat(token.isRefreshTokenExpired()).isFalse();
    }
}
