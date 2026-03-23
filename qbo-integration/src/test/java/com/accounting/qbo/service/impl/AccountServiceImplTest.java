package com.accounting.qbo.service.impl;

import com.accounting.qbo.client.QboApiClient;
import com.accounting.qbo.exception.QboException;
import com.accounting.qbo.model.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountServiceImplTest {

    @Mock
    private QboApiClient apiClient;

    @InjectMocks
    private AccountServiceImpl accountService;

    private static final String REALM = "realm123";

    @BeforeEach
    void setUp() {
        when(apiClient.queryList(any(), any(), eq("Account"), eq(Account.class)))
                .thenReturn(List.of());
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Test
    void findAll_passesCorrectQueryWithOrderByNameAndLimit() {
        accountService.findAll(REALM);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).queryList(eq(REALM), queryCaptor.capture(), eq("Account"), eq(Account.class));

        String q = queryCaptor.getValue();
        assertThat(q).contains("SELECT * FROM Account");
        assertThat(q).contains("ORDERBY Name ASC");
        assertThat(q).contains("MAXRESULTS 1000");
    }

    @Test
    void findAll_returnsAccountsFromClient() {
        Account acct = new Account();
        acct.setId("A-100");
        when(apiClient.queryList(any(), any(), eq("Account"), eq(Account.class)))
                .thenReturn(List.of(acct));

        List<Account> result = accountService.findAll(REALM);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("A-100");
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_delegatesToReadById_withLowercaseEntityName() {
        Account acct = new Account();
        acct.setId("A-42");
        when(apiClient.readById(eq(REALM), eq("account"), eq("A-42"), eq(Account.class)))
                .thenReturn(acct);

        Optional<Account> result = accountService.findById(REALM, "A-42");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("A-42");
    }

    @Test
    void findById_returnsEmptyOptional_whenClientThrows() {
        when(apiClient.readById(any(), any(), any(), any()))
                .thenThrow(new QboException("Account not found"));

        Optional<Account> result = accountService.findById(REALM, "MISSING");
        assertThat(result).isEmpty();
    }

    // ── query ─────────────────────────────────────────────────────────────────

    @Test
    void query_prependsSelectFromAccountWhere() {
        accountService.query(REALM, "AccountType = 'Bank'");

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).queryList(eq(REALM), queryCaptor.capture(), eq("Account"), eq(Account.class));

        assertThat(queryCaptor.getValue())
                .isEqualTo("SELECT * FROM Account WHERE AccountType = 'Bank'");
    }

    // ── findActive ────────────────────────────────────────────────────────────

    @Test
    void findActive_queryContainsActiveFilterAndOrderByName() {
        accountService.findActive(REALM);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).queryList(eq(REALM), queryCaptor.capture(), eq("Account"), eq(Account.class));

        String q = queryCaptor.getValue();
        assertThat(q).contains("Active = true");
        assertThat(q).contains("ORDERBY Name ASC");
    }

    // ── findByType ────────────────────────────────────────────────────────────

    @Test
    void findByType_queryContainsAccountTypeAndActiveFilter() {
        accountService.findByType(REALM, "Bank");

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).queryList(eq(REALM), queryCaptor.capture(), eq("Account"), eq(Account.class));

        String q = queryCaptor.getValue();
        assertThat(q).contains("AccountType = 'Bank'");
        assertThat(q).contains("Active = true");
        assertThat(q).contains("ORDERBY Name ASC");
    }

    @Test
    void findByType_incomeType_queryContainsIncome() {
        accountService.findByType(REALM, "Income");

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).queryList(eq(REALM), queryCaptor.capture(), eq("Account"), eq(Account.class));

        assertThat(queryCaptor.getValue()).contains("AccountType = 'Income'");
    }

    // ── findByClassification ──────────────────────────────────────────────────

    @Test
    void findByClassification_queryContainsClassificationAndActiveFilter() {
        accountService.findByClassification(REALM, "Asset");

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).queryList(eq(REALM), queryCaptor.capture(), eq("Account"), eq(Account.class));

        String q = queryCaptor.getValue();
        assertThat(q).contains("Classification = 'Asset'");
        assertThat(q).contains("Active = true");
    }

    // ── findBalanceSheetAccounts ──────────────────────────────────────────────

    @Test
    void findBalanceSheetAccounts_queryContainsAssetLiabilityEquity() {
        accountService.findBalanceSheetAccounts(REALM);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).queryList(eq(REALM), queryCaptor.capture(), eq("Account"), eq(Account.class));

        String q = queryCaptor.getValue();
        assertThat(q).contains("Asset");
        assertThat(q).contains("Liability");
        assertThat(q).contains("Equity");
        assertThat(q).contains("Active = true");
        assertThat(q).contains("ORDERBY Classification ASC");
        assertThat(q).contains("Name ASC");
    }

    // ── findIncomeStatementAccounts ───────────────────────────────────────────

    @Test
    void findIncomeStatementAccounts_queryContainsRevenueAndExpense() {
        accountService.findIncomeStatementAccounts(REALM);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).queryList(eq(REALM), queryCaptor.capture(), eq("Account"), eq(Account.class));

        String q = queryCaptor.getValue();
        assertThat(q).contains("Revenue");
        assertThat(q).contains("Expense");
        assertThat(q).contains("Active = true");
        assertThat(q).contains("ORDERBY Classification ASC");
    }
}
