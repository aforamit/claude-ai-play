package com.accounting.qbo.service;

import java.util.List;
import java.util.Optional;

/**
 * Generic contract for querying any QBO entity.
 *
 * <p>This is the root extension point for the service layer.
 * Implement this interface (directly or via a specific sub-interface)
 * to add support for new QBO entities (e.g., Bill, Payment, Customer)
 * without modifying any existing code.
 *
 * <p>All methods accept a {@code realmId} (QBO company ID) to support
 * multi-company deployments.
 *
 * @param <T> The domain model type (e.g., Invoice, Deposit, Account)
 */
public interface IQboEntityService<T> {

    /**
     * Returns all entities for the given company (up to QBO's max of 1000).
     * For larger datasets, use {@link #query(String, String)}.
     */
    List<T> findAll(String realmId);

    /**
     * Returns an entity by its QBO ID, or empty if not found.
     */
    Optional<T> findById(String realmId, String id);

    /**
     * Executes a raw QBO SQL WHERE clause.
     *
     * <p>Examples:
     * <pre>
     *   query(realmId, "Balance > '0'")
     *   query(realmId, "TxnDate >= '2024-01-01' AND TxnDate <= '2024-12-31'")
     *   query(realmId, "CustomerRef = '5'")
     * </pre>
     */
    List<T> query(String realmId, String whereClause);
}
