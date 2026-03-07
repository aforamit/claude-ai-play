package com.accounting.qbo.auth;

import java.util.Optional;

/**
 * Contract for persisting and retrieving OAuth tokens.
 *
 * <p>The default implementation is {@link InMemoryTokenStore} (non-persistent).
 * For production, implement this interface backed by a database or encrypted file store.
 *
 * <p>Extension point: implement this interface and register as a Spring bean
 * to replace the default without changing any other code.
 */
public interface TokenStore {

    /** Persist or update a token for the given realmId (company). */
    void save(String realmId, OAuthToken token);

    /** Retrieve a token for the given realmId, if present. */
    Optional<OAuthToken> get(String realmId);

    /** Remove a token (e.g., on disconnect/logout). */
    void delete(String realmId);

    /** Returns true if a token exists for the given realmId. */
    default boolean exists(String realmId) {
        return get(realmId).isPresent();
    }
}
