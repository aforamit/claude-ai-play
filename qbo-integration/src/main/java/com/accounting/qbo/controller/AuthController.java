package com.accounting.qbo.controller;

import com.accounting.qbo.auth.OAuthToken;
import com.accounting.qbo.auth.QboOAuthService;
import com.accounting.qbo.config.QboProperties;
import com.accounting.qbo.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

/**
 * Handles QuickBooks Online OAuth 2.0 Authorization Code Flow.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET  /api/auth/connect             – Redirects to Intuit authorization page</li>
 *   <li>GET  /api/auth/callback            – OAuth callback (exchange code for token)</li>
 *   <li>GET  /api/auth/status/{realmId}    – Check token status</li>
 *   <li>POST /api/auth/refresh/{realmId}   – Force token refresh</li>
 *   <li>DELETE /api/auth/disconnect/{realmId} – Revoke and remove token</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final QboOAuthService oAuthService;
    private final QboProperties props;

    public AuthController(QboOAuthService oAuthService, QboProperties props) {
        this.oAuthService = oAuthService;
        this.props = props;
    }

    /**
     * Step 1 of OAuth flow: redirect user to Intuit authorization page.
     * The user logs in to QuickBooks and grants permission.
     */
    @GetMapping("/connect")
    public ResponseEntity<Void> connect() {
        String state = oAuthService.generateState();
        String authUrl = oAuthService.buildAuthorizationUrl(state);
        log.info("Redirecting to Intuit authorization URL");
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(authUrl))
                .build();
    }

    /**
     * Step 2 of OAuth flow: Intuit redirects back here with an authorization code.
     * Exchange the code for access + refresh tokens.
     */
    @GetMapping("/callback")
    public ResponseEntity<ApiResponse<Map<String, String>>> callback(
            @RequestParam("code") String code,
            @RequestParam("realmId") String realmId,
            @RequestParam(value = "state", required = false) String state) {

        log.info("OAuth callback received for realmId={}", realmId);
        OAuthToken token = oAuthService.exchangeCodeForToken(code, realmId);

        Map<String, String> info = Map.of(
                "realmId", realmId,
                "tokenType", token.tokenType(),
                "expiresIn", String.valueOf(token.expiresIn()),
                "environment", props.isSandbox() ? "sandbox" : "production"
        );

        return ResponseEntity.ok(ApiResponse.ofOne(info));
    }

    /**
     * Check the authorization status for a company (realmId).
     */
    @GetMapping("/status/{realmId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status(
            @PathVariable String realmId) {

        return oAuthService.getTokenStatus(realmId)
                .map(token -> {
                    Map<String, Object> status = Map.of(
                            "authorized", true,
                            "realmId", realmId,
                            "accessTokenExpired", token.isAccessTokenExpired(),
                            "refreshTokenExpired", token.isRefreshTokenExpired(),
                            "environment", props.isSandbox() ? "sandbox" : "production"
                    );
                    return ResponseEntity.ok(ApiResponse.ofOne(status));
                })
                .orElse(ResponseEntity.ok(ApiResponse.ofOne(
                        Map.of("authorized", false, "realmId", realmId))));
    }

    /**
     * Force refresh the access token for a company.
     */
    @PostMapping("/refresh/{realmId}")
    public ResponseEntity<ApiResponse<String>> refresh(@PathVariable String realmId) {
        log.info("Forcing token refresh for realmId={}", realmId);
        oAuthService.refreshToken(realmId);
        return ResponseEntity.ok(ApiResponse.ok("Token refreshed successfully for realmId: " + realmId));
    }

    /**
     * Revoke and remove the token for a company (disconnect from QBO).
     */
    @DeleteMapping("/disconnect/{realmId}")
    public ResponseEntity<ApiResponse<String>> disconnect(@PathVariable String realmId) {
        log.info("Disconnecting realmId={}", realmId);
        oAuthService.revokeToken(realmId);
        return ResponseEntity.ok(ApiResponse.ok("Disconnected from QuickBooks for realmId: " + realmId));
    }
}
