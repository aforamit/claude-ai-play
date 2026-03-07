package com.accounting.qbo.service;

import com.accounting.qbo.model.Account;

import java.util.List;

/**
 * Service contract for QBO Account (Chart of Accounts) operations.
 *
 * <p>Extends {@link IQboEntityService} with account-specific queries.
 */
public interface AccountService extends IQboEntityService<Account> {

    /** Returns only active accounts. */
    List<Account> findActive(String realmId);

    /** Returns accounts matching the given AccountType (e.g., "Bank", "Income"). */
    List<Account> findByType(String realmId, String accountType);

    /** Returns accounts matching the given classification (Asset, Liability, Equity, Revenue, Expense). */
    List<Account> findByClassification(String realmId, String classification);

    /** Returns balance sheet accounts (Asset, Liability, Equity). */
    List<Account> findBalanceSheetAccounts(String realmId);

    /** Returns income statement accounts (Revenue, Expense). */
    List<Account> findIncomeStatementAccounts(String realmId);
}
