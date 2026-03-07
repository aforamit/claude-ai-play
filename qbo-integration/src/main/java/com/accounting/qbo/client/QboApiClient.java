package com.accounting.qbo.client;

import com.accounting.qbo.auth.OAuthToken;
import com.accounting.qbo.auth.QboOAuthService;
import com.accounting.qbo.config.QboProperties;
import com.accounting.qbo.exception.QboException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * Low-level HTTP client for the QuickBooks Online REST API v3.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Build QBO API URIs</li>
 *   <li>Obtain and inject valid Bearer tokens (auto-refresh)</li>
 *   <li>Execute HTTP requests and parse responses</li>
 *   <li>Translate QBO error responses to {@link QboException}</li>
 * </ul>
 *
 * <p>Extension point: inject this bean into new service classes to add
 * support for additional QBO entities without modifying this class.
 */
@Component
public class QboApiClient {

    private static final Logger log = LoggerFactory.getLogger(QboApiClient.class);

    private final QboProperties props;
    private final QboOAuthService oAuthService;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public QboApiClient(QboProperties props, QboOAuthService oAuthService,
                        RestClient qboRestClient, ObjectMapper objectMapper) {
        this.props = props;
        this.oAuthService = oAuthService;
        this.restClient = qboRestClient;
        this.objectMapper = objectMapper;
    }

    // ── Query API ────────────────────────────────────────────────────────────

    /**
     * Executes a QBO SQL-like query and returns the {@code QueryResponse} node.
     *
     * <p>Example queries:
     * <pre>
     *   SELECT * FROM Invoice
     *   SELECT * FROM Invoice WHERE Balance > '0'
     *   SELECT * FROM Deposit WHERE TxnDate >= '2024-01-01'
     *   SELECT * FROM Account WHERE AccountType = 'Bank'
     * </pre>
     *
     * @param realmId  QBO company ID
     * @param qboQuery QBO SQL query string
     * @return {@code QueryResponse} JSON node from the API response
     */
    public JsonNode query(String realmId, String qboQuery) {
        OAuthToken token = oAuthService.getValidToken(realmId);
        URI uri = buildQueryUri(realmId, qboQuery);

        log.debug("QBO Query [realmId={}]: {}", realmId, qboQuery);

        try {
            String responseBody = restClient.get()
                    .uri(uri)
                    .header("Authorization", "Bearer " + token.accessToken())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            checkForQboFault(root);
            return root.get("QueryResponse");
        } catch (QboException e) {
            throw e;
        } catch (Exception e) {
            throw new QboException("QBO API call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method: query and deserialize a list of entities by their JSON array name.
     *
     * @param realmId    QBO company ID
     * @param qboQuery   QBO SQL query string
     * @param entityName JSON array field name in the QueryResponse (e.g., "Invoice")
     * @param entityType Java class to deserialize into
     * @param <T>        Entity type
     * @return List of deserialized entities (empty list if none found)
     */
    public <T> List<T> queryList(String realmId, String qboQuery,
                                  String entityName, Class<T> entityType) {
        JsonNode queryResponse = query(realmId, qboQuery);

        if (queryResponse == null || !queryResponse.has(entityName)) {
            log.debug("No {} found in QueryResponse", entityName);
            return List.of();
        }

        JsonNode entityArray = queryResponse.get(entityName);
        return objectMapper.convertValue(
                entityArray,
                objectMapper.getTypeFactory().constructCollectionType(List.class, entityType)
        );
    }

    // ── Read API (single entity by ID) ───────────────────────────────────────

    /**
     * Reads a single QBO entity by its ID.
     *
     * @param realmId    QBO company ID
     * @param entityName QBO entity type (e.g., "invoice", "deposit", "account")
     * @param entityId   QBO entity ID
     * @param entityType Java class to deserialize into
     * @param <T>        Entity type
     * @return The deserialized entity
     */
    public <T> T readById(String realmId, String entityName, String entityId, Class<T> entityType) {
        OAuthToken token = oAuthService.getValidToken(realmId);
        URI uri = buildReadUri(realmId, entityName, entityId);

        log.debug("QBO Read [realmId={}, entity={}, id={}]", realmId, entityName, entityId);

        try {
            String responseBody = restClient.get()
                    .uri(uri)
                    .header("Authorization", "Bearer " + token.accessToken())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            checkForQboFault(root);

            // QBO Read response: { "EntityName": {...}, "time": "..." }
            // Find the entity node (skip "time" field)
            JsonNode entityNode = root.get(capitalize(entityName));
            if (entityNode == null) {
                throw new QboException("Entity " + entityName + " with id=" + entityId + " not found");
            }
            return objectMapper.treeToValue(entityNode, entityType);
        } catch (QboException e) {
            throw e;
        } catch (Exception e) {
            throw new QboException("QBO Read failed for " + entityName + "/" + entityId + ": " + e.getMessage(), e);
        }
    }

    // ── URI Builders ─────────────────────────────────────────────────────────

    private URI buildQueryUri(String realmId, String qboQuery) {
        return UriComponentsBuilder
                .fromUriString(props.getBaseUrl())
                .pathSegment(props.getApiVersion(), "company", realmId, "query")
                .queryParam("query", qboQuery)
                .queryParam("minorversion", props.getMinorVersion())
                .build()
                .toUri();
    }

    private URI buildReadUri(String realmId, String entityName, String entityId) {
        return UriComponentsBuilder
                .fromUriString(props.getBaseUrl())
                .pathSegment(props.getApiVersion(), "company", realmId,
                        entityName.toLowerCase(), entityId)
                .queryParam("minorversion", props.getMinorVersion())
                .build()
                .toUri();
    }

    // ── Error Handling ───────────────────────────────────────────────────────

    private void checkForQboFault(JsonNode root) {
        if (root.has("Fault")) {
            JsonNode fault = root.get("Fault");
            String type = fault.path("type").asText("UNKNOWN");
            JsonNode errors = fault.get("Error");
            String message = (errors != null && errors.isArray() && !errors.isEmpty())
                    ? errors.get(0).path("Message").asText("Unknown error")
                    : "QBO API fault";
            throw new QboException("QBO API fault [" + type + "]: " + message);
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
