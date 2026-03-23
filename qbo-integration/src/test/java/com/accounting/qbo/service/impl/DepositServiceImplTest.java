package com.accounting.qbo.service.impl;

import com.accounting.qbo.client.QboApiClient;
import com.accounting.qbo.exception.QboException;
import com.accounting.qbo.model.Deposit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DepositServiceImplTest {

    @Mock
    private QboApiClient apiClient;

    @InjectMocks
    private DepositServiceImpl depositService;

    private static final String REALM = "realm123";

    @BeforeEach
    void setUp() {
        when(apiClient.queryList(any(), any(), eq("Deposit"), eq(Deposit.class)))
                .thenReturn(List.of());
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Test
    void findAll_passesCorrectQueryWithOrderAndLimit() {
        depositService.findAll(REALM);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).queryList(eq(REALM), queryCaptor.capture(), eq("Deposit"), eq(Deposit.class));

        String q = queryCaptor.getValue();
        assertThat(q).contains("SELECT * FROM Deposit");
        assertThat(q).contains("ORDERBY TxnDate DESC");
        assertThat(q).contains("MAXRESULTS 1000");
    }

    @Test
    void findAll_returnsDepositsFromClient() {
        Deposit dep = new Deposit();
        dep.setId("D-100");
        when(apiClient.queryList(any(), any(), eq("Deposit"), eq(Deposit.class)))
                .thenReturn(List.of(dep));

        List<Deposit> result = depositService.findAll(REALM);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("D-100");
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_delegatesToReadById_withLowercaseEntityName() {
        Deposit dep = new Deposit();
        dep.setId("D-42");
        when(apiClient.readById(eq(REALM), eq("deposit"), eq("D-42"), eq(Deposit.class)))
                .thenReturn(dep);

        Optional<Deposit> result = depositService.findById(REALM, "D-42");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("D-42");
    }

    @Test
    void findById_returnsEmptyOptional_whenClientThrows() {
        when(apiClient.readById(any(), any(), any(), any()))
                .thenThrow(new QboException("Deposit not found"));

        Optional<Deposit> result = depositService.findById(REALM, "MISSING");
        assertThat(result).isEmpty();
    }

    // ── query ─────────────────────────────────────────────────────────────────

    @Test
    void query_prependsSelectFromDepositWhere() {
        depositService.query(REALM, "TxnDate >= '2026-01-01'");

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).queryList(eq(REALM), queryCaptor.capture(), eq("Deposit"), eq(Deposit.class));

        assertThat(queryCaptor.getValue())
                .isEqualTo("SELECT * FROM Deposit WHERE TxnDate >= '2026-01-01'");
    }

    // ── findByDateRange ───────────────────────────────────────────────────────

    @Test
    void findByDateRange_queryContainsFromAndToDates() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);

        depositService.findByDateRange(REALM, from, to);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).queryList(eq(REALM), queryCaptor.capture(), eq("Deposit"), eq(Deposit.class));

        String q = queryCaptor.getValue();
        assertThat(q).contains("TxnDate >= '2026-03-01'");
        assertThat(q).contains("TxnDate <= '2026-03-31'");
        assertThat(q).contains("ORDERBY TxnDate DESC");
    }

    // ── findByAccountId ───────────────────────────────────────────────────────

    @Test
    void findByAccountId_queryContainsDepositToAccountRef() {
        depositService.findByAccountId(REALM, "51");

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).queryList(eq(REALM), queryCaptor.capture(), eq("Deposit"), eq(Deposit.class));

        String q = queryCaptor.getValue();
        assertThat(q).contains("DepositToAccountRef = '51'");
        assertThat(q).contains("ORDERBY TxnDate DESC");
    }
}
