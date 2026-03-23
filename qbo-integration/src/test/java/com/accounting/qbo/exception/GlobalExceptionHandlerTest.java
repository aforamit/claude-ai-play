package com.accounting.qbo.exception;

import com.accounting.qbo.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    // ── QboException ──────────────────────────────────────────────────────────

    @Test
    void handleQboException_notAuthorizedMessage_returns401() {
        QboException ex = new QboException("Not authorized for realmId: realm1");
        ResponseEntity<ApiResponse<Void>> response = handler.handleQboException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).contains("Not authorized");
    }

    @Test
    void handleQboException_reAuthorizationMessage_returns401() {
        QboException ex = new QboException("Refresh token expired. Re-authorization required.");
        ResponseEntity<ApiResponse<Void>> response = handler.handleQboException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().success()).isFalse();
    }

    @Test
    void handleQboException_apiError_returns502BadGateway() {
        QboException ex = new QboException("QBO API fault [ValidationFault]: Invalid query");
        ResponseEntity<ApiResponse<Void>> response = handler.handleQboException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).contains("ValidationFault");
    }

    @Test
    void handleQboException_genericQboError_returns502() {
        QboException ex = new QboException("Connection timeout to QBO API");
        ResponseEntity<ApiResponse<Void>> response = handler.handleQboException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().success()).isFalse();
    }

    @Test
    void handleQboException_responseBodyContainsOriginalMessage() {
        String errorMsg = "QBO API fault [AuthenticationFault]: Token expired";
        QboException ex = new QboException(errorMsg);
        ResponseEntity<ApiResponse<Void>> response = handler.handleQboException(ex);

        assertThat(response.getBody().message()).isEqualTo(errorMsg);
    }

    // ── IllegalArgumentException ──────────────────────────────────────────────

    @Test
    void handleIllegalArgument_returns400BadRequest() {
        IllegalArgumentException ex = new IllegalArgumentException("CSV column not found: Store ID");
        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgument(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).contains("CSV column not found");
    }

    @Test
    void handleIllegalArgument_responseBodyContainsMessage() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid date format");
        ResponseEntity<ApiResponse<Void>> response = handler.handleIllegalArgument(ex);

        assertThat(response.getBody().message()).isEqualTo("Invalid date format");
    }

    // ── MethodArgumentTypeMismatchException ───────────────────────────────────

    @Test
    void handleTypeMismatch_returns400BadRequest() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("csvPath");
        when(ex.getMessage()).thenReturn("Failed to convert value");

        ResponseEntity<ApiResponse<Void>> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).contains("csvPath");
    }

    // ── Generic Exception ─────────────────────────────────────────────────────

    @Test
    void handleGeneral_returns500InternalServerError() {
        Exception ex = new RuntimeException("Unexpected database error");
        ResponseEntity<ApiResponse<Void>> response = handler.handleGeneral(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().message()).contains("Internal server error");
    }

    @Test
    void handleGeneral_responseBodyContainsExceptionMessage() {
        Exception ex = new RuntimeException("Something broke badly");
        ResponseEntity<ApiResponse<Void>> response = handler.handleGeneral(ex);

        assertThat(response.getBody().message()).contains("Something broke badly");
    }

    @Test
    void handleGeneral_nullPointerException_returns500() {
        Exception ex = new NullPointerException("null value encountered");
        ResponseEntity<ApiResponse<Void>> response = handler.handleGeneral(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ── QboException constructors ─────────────────────────────────────────────

    @Test
    void qboException_withCause_wrapsCorrectly() {
        Throwable cause = new RuntimeException("root cause");
        QboException ex = new QboException("Wrapper message", cause);

        assertThat(ex.getMessage()).isEqualTo("Wrapper message");
        assertThat(ex.getCause()).isEqualTo(cause);

        ResponseEntity<ApiResponse<Void>> response = handler.handleQboException(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }
}
