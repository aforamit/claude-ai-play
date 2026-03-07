package com.accounting.qbo.service.impl;

import com.accounting.qbo.client.QboApiClient;
import com.accounting.qbo.model.Account;
import com.accounting.qbo.service.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Default implementation of {@link AccountService} using the QBO Query API.
 */
@Service
public class AccountServiceImpl implements AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountServiceImpl.class);
    private static final String ENTITY = "Account";

    private final QboApiClient apiClient;

    public AccountServiceImpl(QboApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public List<Account> findAll(String realmId) {
        String q = "SELECT * FROM Account ORDERBY Name ASC MAXRESULTS 1000";
        log.debug("findAll accounts for realmId={}", realmId);
        return apiClient.queryList(realmId, q, ENTITY, Account.class);
    }

    @Override
    public Optional<Account> findById(String realmId, String id) {
        log.debug("findById account id={} realmId={}", id, realmId);
        try {
            Account account = apiClient.readById(realmId, ENTITY.toLowerCase(), id, Account.class);
            return Optional.ofNullable(account);
        } catch (Exception e) {
            log.warn("Account not found: id={}, error={}", id, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<Account> query(String realmId, String whereClause) {
        String q = "SELECT * FROM Account WHERE " + whereClause;
        log.debug("Custom account query: {}", q);
        return apiClient.queryList(realmId, q, ENTITY, Account.class);
    }

    @Override
    public List<Account> findActive(String realmId) {
        String q = "SELECT * FROM Account WHERE Active = true ORDERBY Name ASC";
        log.debug("findActive accounts for realmId={}", realmId);
        return apiClient.queryList(realmId, q, ENTITY, Account.class);
    }

    @Override
    public List<Account> findByType(String realmId, String accountType) {
        String q = "SELECT * FROM Account WHERE AccountType = '" + accountType +
                "' AND Active = true ORDERBY Name ASC";
        log.debug("findByType={} realmId={}", accountType, realmId);
        return apiClient.queryList(realmId, q, ENTITY, Account.class);
    }

    @Override
    public List<Account> findByClassification(String realmId, String classification) {
        String q = "SELECT * FROM Account WHERE Classification = '" + classification +
                "' AND Active = true ORDERBY Name ASC";
        log.debug("findByClassification={} realmId={}", classification, realmId);
        return apiClient.queryList(realmId, q, ENTITY, Account.class);
    }

    @Override
    public List<Account> findBalanceSheetAccounts(String realmId) {
        // Balance sheet: Asset + Liability + Equity
        String q = "SELECT * FROM Account WHERE " +
                "(Classification = 'Asset' OR Classification = 'Liability' OR Classification = 'Equity')" +
                " AND Active = true ORDERBY Classification ASC, Name ASC";
        log.debug("findBalanceSheetAccounts for realmId={}", realmId);
        return apiClient.queryList(realmId, q, ENTITY, Account.class);
    }

    @Override
    public List<Account> findIncomeStatementAccounts(String realmId) {
        // P&L accounts: Revenue + Expense
        String q = "SELECT * FROM Account WHERE " +
                "(Classification = 'Revenue' OR Classification = 'Expense')" +
                " AND Active = true ORDERBY Classification ASC, Name ASC";
        log.debug("findIncomeStatementAccounts for realmId={}", realmId);
        return apiClient.queryList(realmId, q, ENTITY, Account.class);
    }
}
