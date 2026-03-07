package com.accounting.qbo.auth;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link TokenStore}.
 *
 * <p><strong>WARNING:</strong> Tokens are lost on application restart.
 * Replace with a database-backed implementation for production use.
 *
 * <p>Thread-safe via ConcurrentHashMap.
 */
@Component
public class InMemoryTokenStore implements TokenStore {

    private final Map<String, OAuthToken> store = new ConcurrentHashMap<>();

    @Override
    public void save(String realmId, OAuthToken token) {
        store.put(realmId, token);
    }

    @Override
    public Optional<OAuthToken> get(String realmId) {
        return Optional.ofNullable(store.get(realmId));
    }

    @Override
    public void delete(String realmId) {
        store.remove(realmId);
    }

    /** Returns the count of stored tokens (useful for debugging). */
    public int size() {
        return store.size();
    }
}
