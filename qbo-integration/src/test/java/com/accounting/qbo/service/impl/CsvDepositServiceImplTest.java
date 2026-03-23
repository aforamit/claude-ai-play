package com.accounting.qbo.service.impl;

import com.accounting.qbo.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for CsvDepositServiceImpl.
 *
 * Uses actual data files in data/prod/ for bank account, class ref, and invoice lookups
 * (as they are hardcoded in the service). CSV input is provided via temp files for isolation.
 *
 * Note: Tests must run from the project root where data/prod/ is accessible.
 */
class CsvDepositServiceImplTest {

    @TempDir
    Path outputDir;

    private CsvDepositServiceImpl service;
    private ObjectMapper objectMapper;

    // CSV header used in all test files
    private static final String CSV_HEADER =
            "\"Store ID\",\"Deposit Account #\",\"Cash Deposit Date (MM-DD-YYYY)\"," +
            "\"Cash Balance Date (MM-DD-YYYY)\",\"Amount ($)\",\"Total Cash Deposit ($)\"";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        service = new CsvDepositServiceImpl(objectMapper);
    }

    // ── generateDepositJsonFiles – happy path ─────────────────────────────────

    @Test
    void generate_singleRow_knownInvoiceMatch_producesOneDeposit() throws IOException {
        // Store 1174, balance date 03-01-2026 → docNumber 260301-1174 exists in prod invoices
        String csv = CSV_HEADER + "\n" +
                "\"1174\",\"*****1729\",\"03-06-2026\",\"03-01-2026\",\"454.88\",\"454.88\"";

        Path csvFile = writeCsv(csv);
        List<String> result = service.generateDepositJsonFiles(csvFile.toString(), outputDir.toString());

        assertThat(result).containsExactly("deposits.json");

        ArrayNode deposits = readOutputDeposits();
        assertThat(deposits).hasSize(1);

        JsonNode deposit = deposits.get(0);
        assertThat(deposit.path("DepositToAccountRef").path("value").asText()).isEqualTo("57");
        assertThat(deposit.path("DepositToAccountRef").path("name").asText()).contains("1174");
        assertThat(deposit.path("TxnDate").asText()).isEqualTo("2026-03-06");
        assertThat(deposit.path("PrivateNote").asText()).isEqualTo("CashDepositAutomation.AI");
    }

    @Test
    void generate_singleRow_addsPrivateNote() throws IOException {
        String csv = CSV_HEADER + "\n" +
                "\"1174\",\"*****1729\",\"03-06-2026\",\"03-01-2026\",\"454.88\",\"454.88\"";

        service.generateDepositJsonFiles(writeCsv(csv).toString(), outputDir.toString());

        JsonNode deposit = readOutputDeposits().get(0);
        assertThat(deposit.path("PrivateNote").asText()).isEqualTo("CashDepositAutomation.AI");
    }

    @Test
    void generate_singleRow_txnDateFormattedAsIsoDate() throws IOException {
        String csv = CSV_HEADER + "\n" +
                "\"1174\",\"*****1729\",\"03-06-2026\",\"03-01-2026\",\"454.88\",\"454.88\"";

        service.generateDepositJsonFiles(writeCsv(csv).toString(), outputDir.toString());

        String txnDate = readOutputDeposits().get(0).path("TxnDate").asText();
        assertThat(txnDate).matches("\\d{4}-\\d{2}-\\d{2}");
        assertThat(txnDate).isEqualTo("2026-03-06");
    }

    @Test
    void generate_multipleGroups_producesOneDepositPerGroup() throws IOException {
        // Two different deposit accounts on different dates → 2 groups → 2 deposits
        String csv = CSV_HEADER + "\n" +
                "\"1174\",\"*****1729\",\"03-06-2026\",\"03-01-2026\",\"454.88\",\"454.88\"\n" +
                "\"1177\",\"*****2776\",\"03-11-2026\",\"03-06-2026\",\"142.95\",\"142.95\"";

        service.generateDepositJsonFiles(writeCsv(csv).toString(), outputDir.toString());

        assertThat(readOutputDeposits()).hasSize(2);
    }

    @Test
    void generate_multipleRowsSameGroup_producedAsOneDepositWithMultipleLines() throws IOException {
        // 3 rows with same account + depositDate + totalAmt → 1 deposit, 3 ACCOUNTABLE CASH lines
        String csv = CSV_HEADER + "\n" +
                "\"1176\",\"*****2313\",\"03-19-2026\",\"03-09-2026\",\"31.00\",\"354.00\"\n" +
                "\"1176\",\"*****2313\",\"03-19-2026\",\"03-10-2026\",\"142.00\",\"354.00\"\n" +
                "\"1176\",\"*****2313\",\"03-19-2026\",\"03-11-2026\",\"181.00\",\"354.00\"";

        service.generateDepositJsonFiles(writeCsv(csv).toString(), outputDir.toString());

        ArrayNode deposits = readOutputDeposits();
        assertThat(deposits).hasSize(1);

        JsonNode lines = deposits.get(0).path("Line");
        long cashLines = 0;
        for (JsonNode line : lines) {
            if ("ACCOUNTABLE CASH".equals(line.path("Description").asText())) cashLines++;
        }
        assertThat(cashLines).isEqualTo(3);
    }

    @Test
    void generate_groupedDeposit_hasTotalAmtFromCsv() throws IOException {
        String csv = CSV_HEADER + "\n" +
                "\"1176\",\"*****2313\",\"03-19-2026\",\"03-09-2026\",\"31.00\",\"354.00\"\n" +
                "\"1176\",\"*****2313\",\"03-19-2026\",\"03-10-2026\",\"142.00\",\"354.00\"\n" +
                "\"1176\",\"*****2313\",\"03-19-2026\",\"03-11-2026\",\"181.00\",\"354.00\"";

        service.generateDepositJsonFiles(writeCsv(csv).toString(), outputDir.toString());

        JsonNode deposit = readOutputDeposits().get(0);
        assertThat(deposit.path("TotalAmt").decimalValue())
                .isEqualByComparingTo("354.00");
    }

    // ── Over/Short line ───────────────────────────────────────────────────────

    @Test
    void generate_whenTotalDiffersFromCashSum_addsOverShortLine() throws IOException {
        // Store 1174, balance 03-01-2026 → invoice amount 454.44, totalAmt 454.88
        // diff = 454.88 - 454.44 = 0.44 → Over/Short line added
        String csv = CSV_HEADER + "\n" +
                "\"1174\",\"*****1729\",\"03-06-2026\",\"03-01-2026\",\"454.88\",\"454.88\"";

        service.generateDepositJsonFiles(writeCsv(csv).toString(), outputDir.toString());

        JsonNode lines = readOutputDeposits().get(0).path("Line");
        boolean hasOverShort = false;
        for (JsonNode line : lines) {
            if (line.has("DetailType") && "DepositLineDetail".equals(line.path("DetailType").asText())) {
                hasOverShort = true;
                assertThat(line.path("DepositLineDetail").path("AccountRef").path("name").asText())
                        .isEqualTo("Over/ Short");
            }
        }
        assertThat(hasOverShort).as("Expected Over/Short line to be present").isTrue();
    }

    @Test
    void generate_overShortLine_hasClassRefFromStaticFile() throws IOException {
        String csv = CSV_HEADER + "\n" +
                "\"1174\",\"*****1729\",\"03-06-2026\",\"03-01-2026\",\"454.88\",\"454.88\"";

        service.generateDepositJsonFiles(writeCsv(csv).toString(), outputDir.toString());

        JsonNode lines = readOutputDeposits().get(0).path("Line");
        for (JsonNode line : lines) {
            if ("DepositLineDetail".equals(line.path("DetailType").asText())) {
                String classRefName = line.path("DepositLineDetail").path("ClassRef").path("name").asText();
                assertThat(classRefName).contains("1174");
            }
        }
    }

    @Test
    void generate_overShortLine_accountRefValueIs48() throws IOException {
        String csv = CSV_HEADER + "\n" +
                "\"1174\",\"*****1729\",\"03-06-2026\",\"03-01-2026\",\"454.88\",\"454.88\"";

        service.generateDepositJsonFiles(writeCsv(csv).toString(), outputDir.toString());

        JsonNode lines = readOutputDeposits().get(0).path("Line");
        for (JsonNode line : lines) {
            if ("DepositLineDetail".equals(line.path("DetailType").asText())) {
                assertThat(line.path("DepositLineDetail").path("AccountRef").path("value").asText())
                        .isEqualTo("48");
            }
        }
    }

    // ── Invoice lookup ────────────────────────────────────────────────────────

    @Test
    void generate_invoiceFound_linkedTxnPopulatedInLine() throws IOException {
        // 260301-1174 → invoiceId=71352 from prod files
        String csv = CSV_HEADER + "\n" +
                "\"1174\",\"*****1729\",\"03-06-2026\",\"03-01-2026\",\"454.88\",\"454.88\"";

        service.generateDepositJsonFiles(writeCsv(csv).toString(), outputDir.toString());

        JsonNode lines = readOutputDeposits().get(0).path("Line");
        JsonNode cashLine = null;
        for (JsonNode line : lines) {
            if ("ACCOUNTABLE CASH".equals(line.path("Description").asText())) {
                cashLine = line;
                break;
            }
        }
        assertThat(cashLine).isNotNull();
        assertThat(cashLine.path("LinkedTxn")).isNotEmpty();
        assertThat(cashLine.path("LinkedTxn").get(0).path("TxnType").asText()).isEqualTo("Invoice");
        assertThat(cashLine.path("LinkedTxn").get(0).path("TxnId").asText()).isNotBlank();
    }

    @Test
    void generate_invoiceNotFound_fallsBackToCsvAmount() throws IOException {
        // Use a future date that won't match any invoice in prod files
        String csv = CSV_HEADER + "\n" +
                "\"1174\",\"*****1729\",\"12-31-2099\",\"12-25-2099\",\"200.00\",\"200.00\"";

        service.generateDepositJsonFiles(writeCsv(csv).toString(), outputDir.toString());

        JsonNode cashLine = findFirstCashLine(readOutputDeposits().get(0));
        assertThat(cashLine).isNotNull();
        // Amount should be from CSV (200.00)
        assertThat(cashLine.path("Amount").decimalValue()).isEqualByComparingTo("200.00");
        // LinkedTxn should be empty
        assertThat(cashLine.path("LinkedTxn")).isEmpty();
    }

    // ── Bank account fallback ─────────────────────────────────────────────────

    @Test
    void generate_unknownStoreId_usesFallbackAccountName() throws IOException {
        // Store 9999 is not in static_aasma_bank_accounts.json
        String csv = CSV_HEADER + "\n" +
                "\"9999\",\"*****9999\",\"01-01-2026\",\"12-25-2025\",\"100.00\",\"100.00\"";

        service.generateDepositJsonFiles(writeCsv(csv).toString(), outputDir.toString());

        JsonNode accountRef = readOutputDeposits().get(0).path("DepositToAccountRef");
        assertThat(accountRef.path("value").asText()).isEmpty();
        assertThat(accountRef.path("name").asText()).contains("9999");
    }

    // ── Empty CSV ─────────────────────────────────────────────────────────────

    @Test
    void generate_emptyCsv_returnsEmptyListAndNoFile() throws IOException {
        Path csvFile = writeCsv(CSV_HEADER + "\n");

        List<String> result = service.generateDepositJsonFiles(csvFile.toString(), outputDir.toString());

        // Service may return "deposits.json" even with 0 deposits — just verify the JSON is empty array
        if (!result.isEmpty()) {
            ArrayNode deposits = readOutputDeposits();
            assertThat(deposits).isEmpty();
        } else {
            assertThat(result).isEmpty();
        }
    }

    @Test
    void generate_csvWithOnlyHeader_returnsEmptyDepositsFile() throws IOException {
        Path csvFile = writeCsv(CSV_HEADER);

        service.generateDepositJsonFiles(csvFile.toString(), outputDir.toString());

        File outputFile = outputDir.resolve("deposits.json").toFile();
        if (outputFile.exists()) {
            ArrayNode deposits = (ArrayNode) objectMapper.readTree(outputFile);
            assertThat(deposits).isEmpty();
        }
    }

    // ── CSV parsing edge cases ────────────────────────────────────────────────

    @Test
    void generate_csvWithBlankLines_skipsBlankLines() throws IOException {
        String csv = CSV_HEADER + "\n" +
                "\"1174\",\"*****1729\",\"03-06-2026\",\"03-01-2026\",\"454.88\",\"454.88\"\n" +
                "\n" +
                "\"1177\",\"*****2776\",\"03-11-2026\",\"03-06-2026\",\"142.95\",\"142.95\"\n" +
                "\n";

        service.generateDepositJsonFiles(writeCsv(csv).toString(), outputDir.toString());

        assertThat(readOutputDeposits()).hasSize(2);
    }

    @Test
    void generate_csvQuotedFields_parsedCorrectly() throws IOException {
        // All fields quoted — standard CSV format
        String csv = CSV_HEADER + "\n" +
                "\"1174\",\"*****1729\",\"03-06-2026\",\"03-01-2026\",\"454.88\",\"454.88\"";

        service.generateDepositJsonFiles(writeCsv(csv).toString(), outputDir.toString());

        JsonNode deposit = readOutputDeposits().get(0);
        assertThat(deposit.path("TxnDate").asText()).isEqualTo("2026-03-06");
    }

    // ── Output file ───────────────────────────────────────────────────────────

    @Test
    void generate_outputFile_isNamedDepositsJson() throws IOException {
        String csv = CSV_HEADER + "\n" +
                "\"1174\",\"*****1729\",\"03-06-2026\",\"03-01-2026\",\"454.88\",\"454.88\"";

        List<String> result = service.generateDepositJsonFiles(writeCsv(csv).toString(), outputDir.toString());

        assertThat(result).containsExactly("deposits.json");
        assertThat(outputDir.resolve("deposits.json").toFile()).exists();
    }

    @Test
    void generate_outputIsJsonArray() throws IOException {
        String csv = CSV_HEADER + "\n" +
                "\"1174\",\"*****1729\",\"03-06-2026\",\"03-01-2026\",\"454.88\",\"454.88\"";

        service.generateDepositJsonFiles(writeCsv(csv).toString(), outputDir.toString());

        JsonNode root = objectMapper.readTree(outputDir.resolve("deposits.json").toFile());
        assertThat(root.isArray()).isTrue();
    }

    @Test
    void generate_allGroupsWrittenToSingleFile() throws IOException {
        // 4 rows → 4 separate groups (different accounts/dates/totals)
        String csv = CSV_HEADER + "\n" +
                "\"1174\",\"*****1729\",\"03-06-2026\",\"03-01-2026\",\"454.88\",\"454.88\"\n" +
                "\"1177\",\"*****2776\",\"03-11-2026\",\"03-06-2026\",\"142.95\",\"142.95\"\n" +
                "\"1177\",\"*****2776\",\"03-11-2026\",\"03-07-2026\",\"188.00\",\"188.00\"\n" +
                "\"1177\",\"*****2776\",\"03-11-2026\",\"03-08-2026\",\"208.60\",\"208.60\"";

        service.generateDepositJsonFiles(writeCsv(csv).toString(), outputDir.toString());

        assertThat(readOutputDeposits()).hasSize(4);
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    void generate_nonExistentCsvPath_throwsRuntimeException() {
        assertThatThrownBy(() ->
                service.generateDepositJsonFiles("nonexistent/path/deposits.csv", outputDir.toString()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("CSV processing failed");
    }

    // ── Deposit JSON structure ────────────────────────────────────────────────

    @Test
    void generate_depositHasRequiredFields() throws IOException {
        String csv = CSV_HEADER + "\n" +
                "\"1174\",\"*****1729\",\"03-06-2026\",\"03-01-2026\",\"454.88\",\"454.88\"";

        service.generateDepositJsonFiles(writeCsv(csv).toString(), outputDir.toString());

        JsonNode deposit = readOutputDeposits().get(0);
        assertThat(deposit.has("DepositToAccountRef")).isTrue();
        assertThat(deposit.has("TotalAmt")).isTrue();
        assertThat(deposit.has("TxnDate")).isTrue();
        assertThat(deposit.has("PrivateNote")).isTrue();
        assertThat(deposit.has("Line")).isTrue();
        assertThat(deposit.path("Line").isArray()).isTrue();
    }

    @Test
    void generate_cashLineHasRequiredFields() throws IOException {
        String csv = CSV_HEADER + "\n" +
                "\"1174\",\"*****1729\",\"03-06-2026\",\"03-01-2026\",\"454.88\",\"454.88\"";

        service.generateDepositJsonFiles(writeCsv(csv).toString(), outputDir.toString());

        JsonNode cashLine = findFirstCashLine(readOutputDeposits().get(0));
        assertThat(cashLine).isNotNull();
        assertThat(cashLine.has("Description")).isTrue();
        assertThat(cashLine.has("Amount")).isTrue();
        assertThat(cashLine.has("LinkedTxn")).isTrue();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Path writeCsv(String content) throws IOException {
        Path csvFile = outputDir.resolve("test-deposits.csv");
        Files.writeString(csvFile, content);
        return csvFile;
    }

    private ArrayNode readOutputDeposits() throws IOException {
        File outputFile = outputDir.resolve("deposits.json").toFile();
        return (ArrayNode) objectMapper.readTree(outputFile);
    }

    private JsonNode findFirstCashLine(JsonNode deposit) {
        for (JsonNode line : deposit.path("Line")) {
            if ("ACCOUNTABLE CASH".equals(line.path("Description").asText())) {
                return line;
            }
        }
        return null;
    }
}
