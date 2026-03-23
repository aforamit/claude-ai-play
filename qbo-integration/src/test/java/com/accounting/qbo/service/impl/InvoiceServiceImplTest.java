package com.accounting.qbo.service.impl;

import com.accounting.qbo.client.QboApiClient;
import com.accounting.qbo.exception.QboException;
import com.accounting.qbo.model.Invoice;
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
class InvoiceServiceImplTest {

    @Mock
    private QboApiClient apiClient;

    @InjectMocks
    private InvoiceServiceImpl invoiceService;

    private static final String REALM = "realm123";

    @BeforeEach
    void setUp() {
        when(apiClient.queryList(any(), any(), eq("Invoice"), eq(Invoice.class)))
                .thenReturn(List.of());
    }

    // ── findAll ───────────────────────────────────────────────────────────────

    @Test
    void findAll_passesCorrectQueryWithOrderAndLimit() {
        invoiceService.findAll(REALM);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).queryList(eq(REALM), queryCaptor.capture(), eq("Invoice"), eq(Invoice.class));

        String q = queryCaptor.getValue();
        assertThat(q).contains("SELECT * FROM Invoice");
        assertThat(q).contains("ORDERBY TxnDate DESC");
        assertThat(q).contains("MAXRESULTS 1000");
    }

    @Test
    void findAll_returnsListFromClient() {
        Invoice inv = new Invoice();
        inv.setId("100");
        when(apiClient.queryList(any(), any(), eq("Invoice"), eq(Invoice.class)))
                .thenReturn(List.of(inv));

        List<Invoice> result = invoiceService.findAll(REALM);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("100");
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_delegatesToReadById_withLowercaseEntityName() {
        Invoice inv = new Invoice();
        inv.setId("42");
        when(apiClient.readById(eq(REALM), eq("invoice"), eq("42"), eq(Invoice.class)))
                .thenReturn(inv);

        Optional<Invoice> result = invoiceService.findById(REALM, "42");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("42");
    }

    @Test
    void findById_returnsEmptyOptional_whenClientThrows() {
        when(apiClient.readById(any(), any(), any(), any()))
                .thenThrow(new QboException("Not found"));

        Optional<Invoice> result = invoiceService.findById(REALM, "999");
        assertThat(result).isEmpty();
    }

    // ── query ─────────────────────────────────────────────────────────────────

    @Test
    void query_prependsSelectFromInvoiceWhere() {
        invoiceService.query(REALM, "Balance > '0'");

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).queryList(eq(REALM), queryCaptor.capture(), eq("Invoice"), eq(Invoice.class));

        assertThat(queryCaptor.getValue()).isEqualTo("SELECT * FROM Invoice WHERE Balance > '0'");
    }

    // ── findUnpaid ────────────────────────────────────────────────────────────

    @Test
    void findUnpaid_queryContainsBalanceFilterAndOrderByDueDate() {
        invoiceService.findUnpaid(REALM);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).queryList(eq(REALM), queryCaptor.capture(), eq("Invoice"), eq(Invoice.class));

        String q = queryCaptor.getValue();
        assertThat(q).contains("Balance > '0'");
        assertThat(q).contains("ORDERBY DueDate ASC");
    }

    // ── findByCustomerId ──────────────────────────────────────────────────────

    @Test
    void findByCustomerId_queryContainsCustomerRefAndOrderByDate() {
        invoiceService.findByCustomerId(REALM, "CUST-42");

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).queryList(eq(REALM), queryCaptor.capture(), eq("Invoice"), eq(Invoice.class));

        String q = queryCaptor.getValue();
        assertThat(q).contains("CustomerRef = 'CUST-42'");
        assertThat(q).contains("ORDERBY TxnDate DESC");
    }

    // ── findByDateRange ───────────────────────────────────────────────────────

    @Test
    void findByDateRange_queryContainsFromAndToDates() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);

        invoiceService.findByDateRange(REALM, from, to);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).queryList(eq(REALM), queryCaptor.capture(), eq("Invoice"), eq(Invoice.class));

        String q = queryCaptor.getValue();
        assertThat(q).contains("TxnDate >= '2026-03-01'");
        assertThat(q).contains("TxnDate <= '2026-03-31'");
        assertThat(q).contains("ORDERBY TxnDate DESC");
    }

    // ── findOverdue ───────────────────────────────────────────────────────────

    @Test
    void findOverdue_queryContainsBalanceAndDueDateBeforeToday() {
        invoiceService.findOverdue(REALM);

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).queryList(eq(REALM), queryCaptor.capture(), eq("Invoice"), eq(Invoice.class));

        String q = queryCaptor.getValue();
        assertThat(q).contains("Balance > '0'");
        assertThat(q).contains("DueDate < '");
        assertThat(q).contains("ORDERBY DueDate ASC");
        // Verify today's date is embedded (not a fixed date)
        assertThat(q).contains(LocalDate.now().toString());
    }
}
