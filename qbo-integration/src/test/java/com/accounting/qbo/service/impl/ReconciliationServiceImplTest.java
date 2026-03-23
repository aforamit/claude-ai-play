package com.accounting.qbo.service.impl;

import com.accounting.qbo.dto.MatchedTransaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ReconciliationServiceImplTest {

    @TempDir
    Path tempDir;

    private ReconciliationServiceImpl service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        service = new ReconciliationServiceImpl(objectMapper);
    }

    // ── reconcile – end-to-end ────────────────────────────────────────────────

    @Test
    void reconcile_twoMatchedPairs_returnsTwoMatches() throws IOException {
        Path invoiceFile = resourcePath("fixtures/invoices-recon.json");
        Path depositFile = resourcePath("fixtures/deposits-recon.json");
        Path csvOutput = tempDir.resolve("output.csv");

        List<MatchedTransaction> matches = service.reconcile(
                invoiceFile.toString(), depositFile.toString(), csvOutput.toString());

        // invoices-recon.json has IDs 1001 and 1002, deposits-recon.json links both
        assertThat(matches).hasSize(2);
    }

    @Test
    void reconcile_matchedTransaction_invoiceFieldsPopulated() throws IOException {
        Path invoiceFile = resourcePath("fixtures/invoices-recon.json");
        Path depositFile = resourcePath("fixtures/deposits-recon.json");
        Path csvOutput = tempDir.resolve("output.csv");

        List<MatchedTransaction> matches = service.reconcile(
                invoiceFile.toString(), depositFile.toString(), csvOutput.toString());

        MatchedTransaction m = findByInvoiceId(matches, "1001");
        assertThat(m.getInvoiceId()).isEqualTo("1001");
        assertThat(m.getInvoiceDocNumber()).isEqualTo("260301-1174");
        assertThat(m.getInvoiceCustomer()).isEqualTo("AASMA Store 1174");
        assertThat(m.getInvoiceLocation()).isEqualTo("1174 (Mchenry ave)");
        assertThat(m.getInvoiceAccountableCash()).isEqualByComparingTo("-100.00");
    }

    @Test
    void reconcile_matchedTransaction_depositFieldsPopulated() throws IOException {
        Path invoiceFile = resourcePath("fixtures/invoices-recon.json");
        Path depositFile = resourcePath("fixtures/deposits-recon.json");
        Path csvOutput = tempDir.resolve("output.csv");

        List<MatchedTransaction> matches = service.reconcile(
                invoiceFile.toString(), depositFile.toString(), csvOutput.toString());

        MatchedTransaction m = findByInvoiceId(matches, "1001");
        assertThat(m.getDepositId()).isEqualTo("D001");
        assertThat(m.getDepositAccount()).isEqualTo("McHenry#1174 end_1729");
        assertThat(m.getDepositTotalAmount()).isEqualByComparingTo("100.00");
        assertThat(m.getAccountableCashAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void reconcile_depositWithUnknownTxnId_isSkipped() throws IOException {
        // D003 links to TxnId=9999 which doesn't exist in invoices
        Path invoiceFile = resourcePath("fixtures/invoices-recon.json");
        Path depositFile = resourcePath("fixtures/deposits-recon.json");
        Path csvOutput = tempDir.resolve("output.csv");

        List<MatchedTransaction> matches = service.reconcile(
                invoiceFile.toString(), depositFile.toString(), csvOutput.toString());

        // Only D001→1001 and D002→1002 match; D003 is skipped; D004 has no ACCOUNTABLE CASH
        assertThat(matches).hasSize(2);
        assertThat(matches).noneMatch(m -> m.getDepositId().equals("D003"));
    }

    @Test
    void reconcile_depositLineWithoutAccountableCash_isSkipped() throws IOException {
        // D004 has a line with description "OVER SHORT" → not matched
        Path invoiceFile = resourcePath("fixtures/invoices-recon.json");
        Path depositFile = resourcePath("fixtures/deposits-recon.json");
        Path csvOutput = tempDir.resolve("output.csv");

        List<MatchedTransaction> matches = service.reconcile(
                invoiceFile.toString(), depositFile.toString(), csvOutput.toString());

        assertThat(matches).noneMatch(m -> m.getDepositId().equals("D004"));
    }

    @Test
    void reconcile_writesCsvFile() throws IOException {
        Path invoiceFile = resourcePath("fixtures/invoices-recon.json");
        Path depositFile = resourcePath("fixtures/deposits-recon.json");
        Path csvOutput = tempDir.resolve("output.csv");

        service.reconcile(invoiceFile.toString(), depositFile.toString(), csvOutput.toString());

        assertThat(csvOutput.toFile()).exists();
        assertThat(Files.size(csvOutput)).isGreaterThan(0);
    }

    @Test
    void reconcile_csvFile_hasCorrectHeader() throws IOException {
        Path invoiceFile = resourcePath("fixtures/invoices-recon.json");
        Path depositFile = resourcePath("fixtures/deposits-recon.json");
        Path csvOutput = tempDir.resolve("output.csv");

        service.reconcile(invoiceFile.toString(), depositFile.toString(), csvOutput.toString());

        String firstLine = Files.readAllLines(csvOutput).get(0);
        assertThat(firstLine).contains("InvoiceId");
        assertThat(firstLine).contains("DepositId");
        assertThat(firstLine).contains("InvoiceCustomer");
        assertThat(firstLine).contains("AccountableCashAmount");
    }

    @Test
    void reconcile_csvFile_hasDataRows() throws IOException {
        Path invoiceFile = resourcePath("fixtures/invoices-recon.json");
        Path depositFile = resourcePath("fixtures/deposits-recon.json");
        Path csvOutput = tempDir.resolve("output.csv");

        service.reconcile(invoiceFile.toString(), depositFile.toString(), csvOutput.toString());

        List<String> lines = Files.readAllLines(csvOutput);
        // header + 2 data rows
        assertThat(lines).hasSize(3);
        assertThat(lines.get(1)).contains("1001");
        assertThat(lines.get(1)).contains("D001");
    }

    // ── reconcile – empty/edge cases ─────────────────────────────────────────

    @Test
    void reconcile_noDeposits_returnsEmptyList() throws IOException {
        String invoicesJson = """
                {"QueryResponse": {"Invoice": [{"Id": "1001", "DocNumber": "X",
                 "Line": [{"Description": "ACCOUNTABLE CASH", "Amount": -100}]}]}}
                """;
        String depositsJson = """
                {"QueryResponse": {"Deposit": []}}
                """;

        Path invoiceFile = tempDir.resolve("inv.json");
        Path depositFile = tempDir.resolve("dep.json");
        Files.writeString(invoiceFile, invoicesJson);
        Files.writeString(depositFile, depositsJson);

        List<MatchedTransaction> matches = service.reconcile(
                invoiceFile.toString(), depositFile.toString(),
                tempDir.resolve("out.csv").toString());

        assertThat(matches).isEmpty();
    }

    @Test
    void reconcile_depositWithNullLines_isSkipped() throws IOException {
        String invoicesJson = """
                {"QueryResponse": {"Invoice": [{"Id": "1001"}]}}
                """;
        String depositsJson = """
                {"QueryResponse": {"Deposit": [{"Id": "D001", "TotalAmt": 100}]}}
                """;

        Path invoiceFile = tempDir.resolve("inv.json");
        Path depositFile = tempDir.resolve("dep.json");
        Files.writeString(invoiceFile, invoicesJson);
        Files.writeString(depositFile, depositsJson);

        List<MatchedTransaction> matches = service.reconcile(
                invoiceFile.toString(), depositFile.toString(),
                tempDir.resolve("out.csv").toString());

        assertThat(matches).isEmpty();
    }

    @Test
    void reconcile_depositLineWithNoLinkedTxn_isSkipped() throws IOException {
        String invoicesJson = """
                {"QueryResponse": {"Invoice": []}}
                """;
        String depositsJson = """
                {"QueryResponse": {"Deposit": [
                  {"Id": "D001", "TotalAmt": 100,
                   "Line": [{"Description": "ACCOUNTABLE CASH", "Amount": 100}]}
                ]}}
                """;

        Path invoiceFile = tempDir.resolve("inv.json");
        Path depositFile = tempDir.resolve("dep.json");
        Files.writeString(invoiceFile, invoicesJson);
        Files.writeString(depositFile, depositsJson);

        List<MatchedTransaction> matches = service.reconcile(
                invoiceFile.toString(), depositFile.toString(),
                tempDir.resolve("out.csv").toString());

        assertThat(matches).isEmpty();
    }

    // ── resolveLocation fallback ──────────────────────────────────────────────

    @Test
    void reconcile_invoiceWithNoAccountableCashLine_locationFallsBackToFirstClassRef() throws IOException {
        // Invoice 1003 has SALES line (not ACCOUNTABLE CASH) with a ClassRef
        Path invoiceFile = resourcePath("fixtures/invoices-recon.json");

        String depositsJson = """
                {"QueryResponse": {"Deposit": [
                  {"Id": "D003", "TxnDate": "2026-03-03",
                   "DepositToAccountRef": {"value": "51", "name": "Riverbank#1176"},
                   "TotalAmt": 300,
                   "Line": [{"Description": "ACCOUNTABLE CASH", "Amount": 300,
                     "LinkedTxn": [{"TxnId": "1003", "TxnType": "Invoice"}]}]}
                ]}}
                """;
        Path depositFile = tempDir.resolve("dep.json");
        Files.writeString(depositFile, depositsJson);

        List<MatchedTransaction> matches = service.reconcile(
                invoiceFile.toString(), depositFile.toString(),
                tempDir.resolve("out.csv").toString());

        assertThat(matches).hasSize(1);
        // Falls back to ClassRef from SALES line
        assertThat(matches.get(0).getInvoiceLocation()).isEqualTo("1176 (Claribel Road)");
    }

    @Test
    void reconcile_invoiceWithNoClassRefAnywhere_locationIsNull() throws IOException {
        String invoicesJson = """
                {"QueryResponse": {"Invoice": [
                  {"Id": "2001", "DocNumber": "X",
                   "Line": [{"Description": "ACCOUNTABLE CASH", "Amount": -50}]}
                ]}}
                """;
        String depositsJson = """
                {"QueryResponse": {"Deposit": [
                  {"Id": "D-A", "TotalAmt": 50,
                   "DepositToAccountRef": {"value": "51", "name": "Bank"},
                   "Line": [{"Description": "ACCOUNTABLE CASH", "Amount": 50,
                     "LinkedTxn": [{"TxnId": "2001", "TxnType": "Invoice"}]}]}
                ]}}
                """;

        Path invoiceFile = tempDir.resolve("inv.json");
        Path depositFile = tempDir.resolve("dep.json");
        Files.writeString(invoiceFile, invoicesJson);
        Files.writeString(depositFile, depositsJson);

        List<MatchedTransaction> matches = service.reconcile(
                invoiceFile.toString(), depositFile.toString(),
                tempDir.resolve("out.csv").toString());

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getInvoiceLocation()).isNull();
    }

    // ── resolveAccountableCash ────────────────────────────────────────────────

    @Test
    void reconcile_invoiceWithNoLines_accountableCashIsNull() throws IOException {
        String invoicesJson = """
                {"QueryResponse": {"Invoice": [{"Id": "3001"}]}}
                """;
        String depositsJson = """
                {"QueryResponse": {"Deposit": [
                  {"Id": "D-X", "TotalAmt": 50,
                   "DepositToAccountRef": {"value": "51", "name": "Bank"},
                   "Line": [{"Description": "ACCOUNTABLE CASH", "Amount": 50,
                     "LinkedTxn": [{"TxnId": "3001", "TxnType": "Invoice"}]}]}
                ]}}
                """;

        Path invoiceFile = tempDir.resolve("inv.json");
        Path depositFile = tempDir.resolve("dep.json");
        Files.writeString(invoiceFile, invoicesJson);
        Files.writeString(depositFile, depositsJson);

        List<MatchedTransaction> matches = service.reconcile(
                invoiceFile.toString(), depositFile.toString(),
                tempDir.resolve("out.csv").toString());

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getInvoiceAccountableCash()).isNull();
    }

    // ── CSV output edge cases ─────────────────────────────────────────────────

    @Test
    void reconcile_csvRow_nullFieldsOutputAsEmpty() throws IOException {
        // Invoice with no customer ref → customer field empty in CSV
        String invoicesJson = """
                {"QueryResponse": {"Invoice": [
                  {"Id": "4001", "DocNumber": "D-4001",
                   "Line": [{"Description": "ACCOUNTABLE CASH", "Amount": -75}]}
                ]}}
                """;
        String depositsJson = """
                {"QueryResponse": {"Deposit": [
                  {"Id": "D-4", "TotalAmt": 75,
                   "Line": [{"Description": "ACCOUNTABLE CASH", "Amount": 75,
                     "LinkedTxn": [{"TxnId": "4001", "TxnType": "Invoice"}]}]}
                ]}}
                """;

        Path invoiceFile = tempDir.resolve("inv.json");
        Path depositFile = tempDir.resolve("dep.json");
        Files.writeString(invoiceFile, invoicesJson);
        Files.writeString(depositFile, depositsJson);
        Path csvOutput = tempDir.resolve("out.csv");

        service.reconcile(invoiceFile.toString(), depositFile.toString(), csvOutput.toString());

        List<String> lines = Files.readAllLines(csvOutput);
        assertThat(lines).hasSize(2); // header + 1 row
        // customer is null → csvQuote("") → ""
        assertThat(lines.get(1)).contains("\"\"");
    }

    @Test
    void reconcile_customerNameWithComma_quotedInCsv() throws IOException {
        String invoicesJson = """
                {"QueryResponse": {"Invoice": [
                  {"Id": "5001",
                   "CustomerRef": {"value": "99", "name": "Smith, John & Co"},
                   "Line": [{"Description": "ACCOUNTABLE CASH", "Amount": -100}]}
                ]}}
                """;
        String depositsJson = """
                {"QueryResponse": {"Deposit": [
                  {"Id": "D-5", "TotalAmt": 100,
                   "Line": [{"Description": "ACCOUNTABLE CASH", "Amount": 100,
                     "LinkedTxn": [{"TxnId": "5001", "TxnType": "Invoice"}]}]}
                ]}}
                """;

        Path invoiceFile = tempDir.resolve("inv.json");
        Path depositFile = tempDir.resolve("dep.json");
        Files.writeString(invoiceFile, invoicesJson);
        Files.writeString(depositFile, depositsJson);
        Path csvOutput = tempDir.resolve("out.csv");

        service.reconcile(invoiceFile.toString(), depositFile.toString(), csvOutput.toString());

        String dataRow = Files.readAllLines(csvOutput).get(1);
        // Customer name has comma so must be quoted
        assertThat(dataRow).contains("\"Smith, John & Co\"");
    }

    @Test
    void reconcile_customerNameWithQuote_escapedInCsv() throws IOException {
        String invoicesJson = """
                {"QueryResponse": {"Invoice": [
                  {"Id": "6001",
                   "CustomerRef": {"value": "88", "name": "O\\"Brien"},
                   "Line": [{"Description": "ACCOUNTABLE CASH", "Amount": -50}]}
                ]}}
                """;
        String depositsJson = """
                {"QueryResponse": {"Deposit": [
                  {"Id": "D-6", "TotalAmt": 50,
                   "Line": [{"Description": "ACCOUNTABLE CASH", "Amount": 50,
                     "LinkedTxn": [{"TxnId": "6001", "TxnType": "Invoice"}]}]}
                ]}}
                """;

        Path invoiceFile = tempDir.resolve("inv.json");
        Path depositFile = tempDir.resolve("dep.json");
        Files.writeString(invoiceFile, invoicesJson);
        Files.writeString(depositFile, depositsJson);
        Path csvOutput = tempDir.resolve("out.csv");

        service.reconcile(invoiceFile.toString(), depositFile.toString(), csvOutput.toString());

        String dataRow = Files.readAllLines(csvOutput).get(1);
        // Embedded quote should be escaped as ""
        assertThat(dataRow).contains("\"\"");
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    void reconcile_nonExistentInvoiceFile_throwsRuntimeException() {
        assertThatThrownBy(() ->
                service.reconcile("nonexistent/invoices.json",
                        resourcePath("fixtures/deposits-recon.json").toString(),
                        tempDir.resolve("out.csv").toString()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void reconcile_nonExistentDepositFile_throwsRuntimeException() {
        assertThatThrownBy(() ->
                service.reconcile(resourcePath("fixtures/invoices-recon.json").toString(),
                        "nonexistent/deposits.json",
                        tempDir.resolve("out.csv").toString()))
                .isInstanceOf(RuntimeException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Path resourcePath(String relativePath) {
        String path = getClass().getClassLoader().getResource(relativePath).getPath();
        // On Windows, path may start with /C:/ — normalize it
        if (path.startsWith("/") && path.length() > 2 && path.charAt(2) == ':') {
            path = path.substring(1);
        }
        return Path.of(path);
    }

    private MatchedTransaction findByInvoiceId(List<MatchedTransaction> matches, String invoiceId) {
        return matches.stream()
                .filter(m -> invoiceId.equals(m.getInvoiceId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No match found for invoiceId=" + invoiceId));
    }
}
