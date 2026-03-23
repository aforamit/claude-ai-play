package com.accounting.qbo.client;

import com.accounting.qbo.auth.OAuthToken;
import com.accounting.qbo.auth.QboOAuthService;
import com.accounting.qbo.config.QboProperties;
import com.accounting.qbo.exception.QboException;
import com.accounting.qbo.model.Invoice;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class QboApiClientTest {

    @Mock
    private QboOAuthService oAuthService;

    @Mock
    private RestClient restClient;

    private QboApiClient apiClient;
    private ObjectMapper objectMapper;

    // RestClient fluent chain mocks
    @Mock RestClient.RequestHeadersUriSpec uriSpec;
    @Mock RestClient.RequestHeadersSpec headersSpec;
    @Mock RestClient.ResponseSpec responseSpec;

    private static final String REALM = "realm123";
    private static final OAuthToken VALID_TOKEN =
            new OAuthToken("access-token", "refresh-token", "Bearer", 3600,
                    Instant.now(), REALM);

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        QboProperties props = new QboProperties();
        props.setSandbox(true);
        props.setClientId("test-client");
        props.setClientSecret("test-secret");

        apiClient = new QboApiClient(props, oAuthService, restClient, objectMapper);

        // Default GET chain
        when(restClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(URI.class))).thenReturn(headersSpec);
        when(headersSpec.header(anyString(), anyString())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);

        // Default valid token
        when(oAuthService.getValidToken(REALM)).thenReturn(VALID_TOKEN);
    }

    // ── query ─────────────────────────────────────────────────────────────────

    @Test
    void query_returnsQueryResponseNode() {
        String json = "{\"QueryResponse\": {\"Invoice\": [], \"startPosition\": 1}}";
        when(responseSpec.body(String.class)).thenReturn(json);

        var result = apiClient.query(REALM, "SELECT * FROM Invoice");

        assertThat(result).isNotNull();
        assertThat(result.has("Invoice")).isTrue();
    }

    @Test
    void query_throwsQboException_whenFaultInResponse() {
        String faultJson = """
                {
                  "Fault": {
                    "Error": [{"Message": "Invalid token", "code": "401"}],
                    "type": "AuthenticationFault"
                  }
                }
                """;
        when(responseSpec.body(String.class)).thenReturn(faultJson);

        assertThatThrownBy(() -> apiClient.query(REALM, "SELECT * FROM Invoice"))
                .isInstanceOf(QboException.class)
                .hasMessageContaining("AuthenticationFault")
                .hasMessageContaining("Invalid token");
    }

    @Test
    void query_throwsQboException_onHttpError() {
        when(responseSpec.body(String.class)).thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> apiClient.query(REALM, "SELECT * FROM Invoice"))
                .isInstanceOf(QboException.class)
                .hasMessageContaining("QBO API call failed");
    }

    @Test
    void query_injectsAuthorizationBearerToken() {
        when(responseSpec.body(String.class)).thenReturn("{\"QueryResponse\": {}}");

        apiClient.query(REALM, "SELECT * FROM Invoice");

        verify(headersSpec).header("Authorization", "Bearer access-token");
    }

    @Test
    void query_buildsCorrectQueryUri_containsRealmAndEntity() {
        when(responseSpec.body(String.class)).thenReturn("{\"QueryResponse\": {}}");

        apiClient.query(REALM, "SELECT * FROM Invoice");

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(uriSpec).uri(uriCaptor.capture());

        URI uri = uriCaptor.getValue();
        assertThat(uri.toString()).contains("sandbox-quickbooks.api.intuit.com");
        assertThat(uri.toString()).contains("v3");
        assertThat(uri.toString()).contains(REALM);
        assertThat(uri.toString()).contains("query");
        assertThat(uri.getQuery()).contains("minorversion=65");
    }

    // ── queryList ─────────────────────────────────────────────────────────────

    @Test
    void queryList_returnsDeserializedList() {
        String json = """
                {
                  "QueryResponse": {
                    "Invoice": [
                      {"Id": "101", "DocNumber": "INV-001"},
                      {"Id": "102", "DocNumber": "INV-002"}
                    ]
                  }
                }
                """;
        when(responseSpec.body(String.class)).thenReturn(json);

        List<Invoice> result = apiClient.queryList(REALM, "SELECT * FROM Invoice", "Invoice", Invoice.class);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo("101");
        assertThat(result.get(1).getId()).isEqualTo("102");
    }

    @Test
    void queryList_returnsEmptyList_whenEntityNotInQueryResponse() {
        when(responseSpec.body(String.class)).thenReturn("{\"QueryResponse\": {}}");

        List<Invoice> result = apiClient.queryList(REALM, "SELECT * FROM Invoice", "Invoice", Invoice.class);

        assertThat(result).isEmpty();
    }

    @Test
    void queryList_returnsEmptyList_whenQueryResponseIsNull() {
        when(responseSpec.body(String.class)).thenReturn("{}");

        List<Invoice> result = apiClient.queryList(REALM, "SELECT * FROM Invoice", "Invoice", Invoice.class);

        assertThat(result).isEmpty();
    }

    // ── readById ──────────────────────────────────────────────────────────────

    @Test
    void readById_returnsDeserializedEntity() {
        String json = """
                {
                  "Invoice": {"Id": "55", "DocNumber": "260301-1174"},
                  "time": "2026-03-01T10:00:00"
                }
                """;
        when(responseSpec.body(String.class)).thenReturn(json);

        Invoice result = apiClient.readById(REALM, "invoice", "55", Invoice.class);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo("55");
        assertThat(result.getDocNumber()).isEqualTo("260301-1174");
    }

    @Test
    void readById_throwsQboException_whenEntityNotInResponse() {
        when(responseSpec.body(String.class)).thenReturn("{\"time\": \"2026-03-01\"}");

        assertThatThrownBy(() -> apiClient.readById(REALM, "invoice", "999", Invoice.class))
                .isInstanceOf(QboException.class)
                .hasMessageContaining("invoice")
                .hasMessageContaining("999");
    }

    @Test
    void readById_buildsCorrectReadUri_containsEntityAndId() {
        String json = "{\"Invoice\": {\"Id\": \"42\"}}";
        when(responseSpec.body(String.class)).thenReturn(json);

        apiClient.readById(REALM, "invoice", "42", Invoice.class);

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(uriSpec).uri(uriCaptor.capture());

        String uriStr = uriCaptor.getValue().toString();
        assertThat(uriStr).contains(REALM);
        assertThat(uriStr).contains("invoice");
        assertThat(uriStr).contains("42");
        assertThat(uriStr).contains("minorversion=65");
    }

    @Test
    void readById_throwsQboException_whenFaultInResponse() {
        String faultJson = """
                {
                  "Fault": {
                    "Error": [{"Message": "Unauthorized"}],
                    "type": "AuthenticationFault"
                  }
                }
                """;
        when(responseSpec.body(String.class)).thenReturn(faultJson);

        assertThatThrownBy(() -> apiClient.readById(REALM, "invoice", "1", Invoice.class))
                .isInstanceOf(QboException.class)
                .hasMessageContaining("AuthenticationFault");
    }

    // ── Fault handling ────────────────────────────────────────────────────────

    @Test
    void query_faultWithNoErrors_includesGenericMessage() {
        String faultJson = """
                {
                  "Fault": {
                    "type": "ValidationFault"
                  }
                }
                """;
        when(responseSpec.body(String.class)).thenReturn(faultJson);

        assertThatThrownBy(() -> apiClient.query(REALM, "SELECT * FROM Invoice"))
                .isInstanceOf(QboException.class)
                .hasMessageContaining("ValidationFault");
    }

    // ── Token injection ───────────────────────────────────────────────────────

    @Test
    void readById_getsValidTokenBeforeRequest() {
        String json = "{\"Invoice\": {\"Id\": \"1\"}}";
        when(responseSpec.body(String.class)).thenReturn(json);

        apiClient.readById(REALM, "invoice", "1", Invoice.class);

        verify(oAuthService).getValidToken(REALM);
    }
}
