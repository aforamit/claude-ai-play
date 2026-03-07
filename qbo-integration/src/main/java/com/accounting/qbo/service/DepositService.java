package com.accounting.qbo.service;

import com.accounting.qbo.model.Deposit;

import java.time.LocalDate;
import java.util.List;

/**
 * Service contract for QBO Deposit operations.
 *
 * <p>Extends {@link IQboEntityService} with deposit-specific queries.
 */
public interface DepositService extends IQboEntityService<Deposit> {

    /** Returns all deposits within the given date range (inclusive). */
    List<Deposit> findByDateRange(String realmId, LocalDate from, LocalDate to);

    /** Returns all deposits posted to a specific bank/clearing account. */
    List<Deposit> findByAccountId(String realmId, String accountId);
}
