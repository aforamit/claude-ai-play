package com.accounting.qbo.auth;

import com.accounting.qbo.config.QboProperties;
import com.accounting.qbo.exception.QboException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QboOAuthServiceTest {

    @Mock
    private TokenStore tokenStore;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec bodyUriSpec;

    @Mock(answer = Answers.RETURNS_SELF)
    private RestClient.RequestBodySpec bodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private QboOAuthService oAuthService;
    private QboProperties props;

    private static final String REALM = "realm123";
    private static final String VALID_TOKEN_RESPONSE =
            "{\"access_token\":\"newAccess\",\"refresh_token\":\"newRefresh\"," +
            "\"token_type\":\"Bearer\",\"expires_in\":3600}";

    @BeforeEach
    void setUp() {
        props = new QboProperties();
        props.setClientId("clientId123");
        props.setClientSecret("clientSecret456");
        props.setRedirectUri("http://localhost:8080/api/auth/callback");

        oAuthService = new QboOAuthService(props, tokenStore, restClient, new ObjectMapper());
    }

    // ── buildAuthorizationUrl ─────────────────────────────────────────────────

    @Test
    void buildAuthorizationUrl_containsClientId() {
        String url = oAuthService.buildAuthorizationUrl("state123");
        assertThat(url).contains("client_id=clientId123");
    }

    @Test
    void buildAuthorizationUrl_containsEncodedRedirectUri() {
        String url = oAuthService.buildAuthorizationUrl("state123");
        assertThat(url).contains("redirect_uri=");
        assertThat(url).contains("localhost");
    }

    @Test
    void buildAuthorizationUrl_containsResponseTypeCode() {
        String url = oAuthService.buildAuthorizationUrl("state123");
        assertThat(url).contains("response_type=code");
    }

    @Test
    void buildAuthorizationUrl_containsState() {
        String url = oAuthService.buildAuthorizationUrl("myState");
        assertThat(url).contains("state=myState");
    }

    @Test
    void buildAuthorizationUrl_startsWithIntuitAuthUrl() {
        String url = oAuthService.buildAuthorizationUrl("s");
        assertThat(url).startsWith(QboProperties.INTUIT_AUTH_URL);
    }

    // ── generateState ─────────────────────────────────────────────────────────

    @Test
    void generateState_returnsNonNullNonEmptyString() {
        String state = oAuthService.generateState();
        assertThat(state).isNotNull().isNotEmpty();
    }

    @Test
    void generateState_returnsDifferentValuesEachCall() {
        String s1 = oAuthService.generateState();
        String s2 = oAuthService.generateState();
        assertThat(s1).isNotEqualTo(s2);
    }

    // ── getValidToken ─────────────────────────────────────────────────────────

    @Test
    void getValidToken_returnsToken_whenAccessTokenNotExpired() {
        OAuthToken validToken = OAuthToken.of("access", "refresh", "Bearer", 3600, REALM);
        when(tokenStore.get(REALM)).thenReturn(Optional.of(validToken));

        OAuthToken result = oAuthService.getValidToken(REALM);

        assertThat(result.accessToken()).isEqualTo("access");
        verify(tokenStore, never()).save(any(), any()); // no refresh happened
    }

    @Test
    void getValidToken_throwsQboException_whenNoTokenForRealm() {
        when(tokenStore.get(REALM)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> oAuthService.getValidToken(REALM))
                .isInstanceOf(QboException.class)
                .hasMessageContaining("Not authorized")
                .hasMessageContaining(REALM);
    }

    @Test
    void getValidToken_autoRefreshes_whenAccessTokenExpired() {
        // Expired access token (issued 4000s ago with 3600s expiry)
        OAuthToken expiredToken = new OAuthToken("oldAccess", "refreshToken", "Bearer", 3600,
                Instant.now().minusSeconds(4000), REALM);
        when(tokenStore.get(REALM)).thenReturn(Optional.of(expiredToken));

        // Setup POST for token refresh
        setupTokenExchangeMock();

        OAuthToken result = oAuthService.getValidToken(REALM);

        assertThat(result.accessToken()).isEqualTo("newAccess");
        verify(tokenStore).save(eq(REALM), any(OAuthToken.class));
    }

    // ── refreshToken ──────────────────────────────────────────────────────────

    @Test
    void refreshToken_throwsQboException_whenNoTokenExists() {
        when(tokenStore.get(REALM)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> oAuthService.refreshToken(REALM))
                .isInstanceOf(QboException.class)
                .hasMessageContaining(REALM);
    }

    @Test
    void refreshToken_throwsQboException_andDeletesToken_whenRefreshTokenExpired() {
        // Refresh token expired (issued > 100 days ago)
        OAuthToken expiredRefresh = new OAuthToken("access", "refreshToken", "Bearer", 3600,
                Instant.now().minusSeconds(8_650_000L), REALM);
        when(tokenStore.get(REALM)).thenReturn(Optional.of(expiredRefresh));

        assertThatThrownBy(() -> oAuthService.refreshToken(REALM))
                .isInstanceOf(QboException.class)
                .hasMessageContaining("Refresh token expired")
                .hasMessageContaining("Re-authorization");

        verify(tokenStore).delete(REALM);
    }

    @Test
    void refreshToken_savesNewToken_whenSuccessful() {
        OAuthToken token = OAuthToken.of("oldAccess", "refreshToken", "Bearer", 3600, REALM);
        when(tokenStore.get(REALM)).thenReturn(Optional.of(token));

        setupTokenExchangeMock();

        OAuthToken result = oAuthService.refreshToken(REALM);

        assertThat(result.accessToken()).isEqualTo("newAccess");
        assertThat(result.refreshToken()).isEqualTo("newRefresh");
        verify(tokenStore).save(eq(REALM), argThat(t -> t.accessToken().equals("newAccess")));
    }

    // ── exchangeCodeForToken ──────────────────────────────────────────────────

    @Test
    void exchangeCodeForToken_savesToken_andReturnsIt() {
        setupTokenExchangeMock();

        OAuthToken result = oAuthService.exchangeCodeForToken("authCode123", REALM);

        assertThat(result.accessToken()).isEqualTo("newAccess");
        assertThat(result.realmId()).isEqualTo(REALM);
        verify(tokenStore).save(eq(REALM), any(OAuthToken.class));
    }

    // ── isAuthorized ──────────────────────────────────────────────────────────

    @Test
    void isAuthorized_returnsTrue_whenTokenExists() {
        when(tokenStore.exists(REALM)).thenReturn(true);
        assertThat(oAuthService.isAuthorized(REALM)).isTrue();
    }

    @Test
    void isAuthorized_returnsFalse_whenNoToken() {
        when(tokenStore.exists(REALM)).thenReturn(false);
        assertThat(oAuthService.isAuthorized(REALM)).isFalse();
    }

    // ── getTokenStatus ────────────────────────────────────────────────────────

    @Test
    void getTokenStatus_returnsToken_whenPresent() {
        OAuthToken token = OAuthToken.of("a", "r", "Bearer", 3600, REALM);
        when(tokenStore.get(REALM)).thenReturn(Optional.of(token));

        Optional<OAuthToken> status = oAuthService.getTokenStatus(REALM);
        assertThat(status).isPresent();
        assertThat(status.get().accessToken()).isEqualTo("a");
    }

    @Test
    void getTokenStatus_returnsEmpty_whenNoToken() {
        when(tokenStore.get(REALM)).thenReturn(Optional.empty());

        Optional<OAuthToken> status = oAuthService.getTokenStatus(REALM);
        assertThat(status).isEmpty();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setupTokenExchangeMock() {
        // Use individual mocks instead of RETURNS_DEEP_STUBS to avoid varargs matching issues
        // with header(String name, String... values). RETURNS_SELF on bodySpec handles the
        // header/contentType/body builder chain automatically without needing argument matchers.
        when(restClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.uri(anyString())).thenReturn(bodySpec);
        // header(), contentType(), body() self-return via RETURNS_SELF — no stubbing needed
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn(VALID_TOKEN_RESPONSE);
    }
}
