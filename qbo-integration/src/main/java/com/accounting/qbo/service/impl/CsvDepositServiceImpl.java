package com.accounting.qbo.service.impl;

import com.accounting.qbo.service.CsvDepositService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a deposits CSV and produces one QBO-compatible deposit JSON per row.
 *
 * <p>CSV columns (header row required):
 * <pre>
 * "Store ID","Deposit Account #","Cash Deposit Date (MM-DD-YYYY)",
 * "Cash Balance Date (MM-DD-YYYY)","Amount ($)","Total Cash Deposit ($)"
 * </pre>
 *
 * <p>Output JSON mirrors the input section of {@code data/sample/new_test_deposit.json}.
 */
@Service
public class CsvDepositServiceImpl implements CsvDepositService {

    private static final Logger log = LoggerFactory.getLogger(CsvDepositServiceImpl.class);

    private static final DateTimeFormatter CSV_DATE_FMT  = DateTimeFormatter.ofPattern("MM-dd-yyyy");
    private static final DateTimeFormatter JSON_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String BANK_ACCOUNTS_JSON = "data/prod/static_aasma_bank_accounts.json";
    private static final String CLASSREFS_JSON     = "data/prod/static_aasma_classrefs.json";

    /** DocNumber date portion format: last-2-year + month + day → e.g. 260301 */
    private static final DateTimeFormatter DOC_DATE_FMT = DateTimeFormatter.ofPattern("yyMMdd");

    private final ObjectMapper objectMapper;

    /** Lazily loaded cache: storeId → [qboId, accountName] */
    private Map<String, String[]> bankAccountIndex;

    /** Lazily loaded cache: storeId → [classId, className] */
    private Map<String, String[]> classRefIndex;

    /** Cached invoice line data per invoices JSON path: docNumber → InvoiceLineRef */
    private String loadedInvoicesPath;
    private Map<String, InvoiceLineRef> invoiceIndex;

    public CsvDepositServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Holds the data needed to build a deposit Line item from a matched invoice. */
    private record InvoiceLineRef(String invoiceId, String lineId, BigDecimal amount) {}

    @Override
    public List<String> generateDepositJsonFiles(String csvPath, String invoicesJson, String outputDir) {
        List<String> generated = new ArrayList<>();
        ArrayNode allDeposits = objectMapper.createArrayNode();

        try {
            Files.createDirectories(Paths.get(outputDir));

            try (BufferedReader reader = new BufferedReader(new FileReader(csvPath))) {
                String headerLine = reader.readLine();
                if (headerLine == null) {
                    log.warn("CSV file is empty: {}", csvPath);
                    return generated;
                }

                String[] headers = parseCsvLine(headerLine);
                int idxStoreId      = indexOf(headers, "Store ID");
                int idxAccountNum   = indexOf(headers, "Deposit Account #");
                int idxDepositDate  = indexOf(headers, "Cash Deposit Date (MM-DD-YYYY)");
                int idxBalanceDate  = indexOf(headers, "Cash Balance Date (MM-DD-YYYY)");
                int idxAmount       = indexOf(headers, "Amount ($)");
                int idxTotalAmt     = indexOf(headers, "Total Cash Deposit ($)");

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    String[] cols = parseCsvLine(line);

                    String storeId      = cols[idxStoreId].trim();
                    String accountNum   = cols[idxAccountNum].trim();
                    String depositDate  = cols[idxDepositDate].trim();
                    String balanceDate  = cols[idxBalanceDate].trim();
                    String amount       = cols[idxAmount].trim();
                    String totalAmt     = cols[idxTotalAmt].trim();

                    LocalDate txnDate   = LocalDate.parse(depositDate, CSV_DATE_FMT);
                    String txnDateStr   = txnDate.format(JSON_DATE_FMT);

                    String[] account    = lookupBankAccount(storeId);
                    String accountId    = account[0];
                    String accountName  = account[1];

                    String[] classRef   = lookupClassRef(storeId);

                    // DocNumber = YYMMDD (from Cash Balance Date) + "-" + storeId
                    LocalDate balDate   = LocalDate.parse(balanceDate, CSV_DATE_FMT);
                    String docNumber    = balDate.format(DOC_DATE_FMT) + "-" + storeId;
                    InvoiceLineRef inv  = lookupInvoiceLine(invoicesJson, docNumber);

                    ObjectNode deposit = buildDepositJson(accountId, accountName, txnDateStr,
                            new BigDecimal(amount), new BigDecimal(totalAmt), inv, classRef);

                    allDeposits.add(deposit);
                    log.info("Built deposit for storeId={} txnDate={}", storeId, txnDateStr);
                }
            }

            // Write all deposits to a single output file
            String outputFile = outputDir + "/deposits.json";
            try (FileWriter writer = new FileWriter(outputFile)) {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(writer, allDeposits);
            }
            log.info("Written {} deposit(s) to {}", allDeposits.size(), outputFile);
            generated.add("deposits.json");

        } catch (Exception e) {
            log.error("Failed to generate deposit JSON files from CSV: {}", csvPath, e);
            throw new RuntimeException("CSV processing failed: " + e.getMessage(), e);
        }

        return generated;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ObjectNode buildDepositJson(String accountId, String accountName,
                                        String txnDate, BigDecimal amount, BigDecimal totalAmt,
                                        InvoiceLineRef inv, String[] classRef) {
        ObjectNode root = objectMapper.createObjectNode();

        // DepositToAccountRef — value from static bank accounts lookup
        ObjectNode accountRef = objectMapper.createObjectNode();
        accountRef.put("value", accountId);
        accountRef.put("name", accountName);
        root.set("DepositToAccountRef", accountRef);

        root.put("TotalAmt", totalAmt);
        root.put("TxnDate", txnDate);

        // Line array — ACCOUNTABLE CASH line linked to the matched invoice
        ArrayNode lines = objectMapper.createArrayNode();

        BigDecimal cashAmount = inv != null ? inv.amount().abs() : amount;

        ObjectNode cashLine = objectMapper.createObjectNode();
        cashLine.put("Description", "ACCOUNTABLE CASH");
        cashLine.put("Amount", cashAmount);

        ArrayNode linkedTxn = objectMapper.createArrayNode();
        if (inv != null) {
            ObjectNode txnRef = objectMapper.createObjectNode();
            txnRef.put("TxnId",     inv.invoiceId());
            txnRef.put("TxnType",   "Invoice");
            txnRef.put("TxnLineId", inv.lineId());
            linkedTxn.add(txnRef);
        }
        cashLine.set("LinkedTxn", linkedTxn);
        lines.add(cashLine);

        // Over/Short line — added when TotalAmt ≠ sum of ACCOUNTABLE CASH line amount
        BigDecimal diff = totalAmt.subtract(cashAmount).stripTrailingZeros();
        if (diff.compareTo(BigDecimal.ZERO) != 0 && inv != null) {
            ObjectNode overShort = objectMapper.createObjectNode();
            overShort.put("Id",         "1");
            overShort.put("LineNum",     1);
            overShort.put("Amount",      diff);
            overShort.put("DetailType", "DepositLineDetail");

            ObjectNode detail = objectMapper.createObjectNode();

            ObjectNode classRefNode = objectMapper.createObjectNode();
            classRefNode.put("value", classRef[0]);
            classRefNode.put("name",  classRef[1]);
            detail.set("ClassRef", classRefNode);

            ObjectNode overShortRef = objectMapper.createObjectNode();
            overShortRef.put("value", "48");
            overShortRef.put("name",  "Over/ Short");
            detail.set("AccountRef", overShortRef);

            overShort.set("DepositLineDetail", detail);
            overShort.set("CustomExtensions", objectMapper.createArrayNode());
            lines.add(overShort);
        }

        root.set("Line", lines);

        return root;
    }

    /**
     * Looks up the QBO account entry for the given storeId.
     * Matches accounts whose Name contains {@code #storeId} (e.g. "McHenry#1174 end_1729").
     *
     * @return String[2] — [qboId, accountName]; falls back to ["", "Store#"+storeId] if not found
     */
    private String[] lookupBankAccount(String storeId) {
        if (bankAccountIndex == null) {
            bankAccountIndex = loadBankAccountIndex(BANK_ACCOUNTS_JSON);
        }
        String[] entry = bankAccountIndex.get(storeId);
        if (entry == null) {
            log.warn("No bank account found for storeId={} in {}", storeId, BANK_ACCOUNTS_JSON);
            return new String[]{"", "Store#" + storeId};
        }
        return entry;
    }

    /**
     * Parses the static bank accounts JSON and builds a storeId → [qboId, name] index.
     * A store account is identified by {@code #storeId} appearing in the account Name.
     */
    private Map<String, String[]> loadBankAccountIndex(String jsonPath) {
        Map<String, String[]> index = new HashMap<>();
        try {
            JsonNode root     = objectMapper.readTree(new File(jsonPath));
            JsonNode accounts = root.path("QueryResponse").path("Account");
            for (JsonNode account : accounts) {
                String name = account.path("Name").asText("");
                String id   = account.path("Id").asText("");
                // extract storeId from names like "McHenry#1174 end_1729"
                int hash = name.indexOf('#');
                if (hash >= 0) {
                    int end = hash + 1;
                    while (end < name.length() && Character.isDigit(name.charAt(end))) end++;
                    String storeId = name.substring(hash + 1, end);
                    if (!storeId.isEmpty()) {
                        index.put(storeId, new String[]{id, name});
                        log.debug("Indexed bank account: storeId={} → id={}, name={}", storeId, id, name);
                    }
                }
            }
            log.info("Loaded {} store bank accounts from {}", index.size(), jsonPath);
        } catch (Exception e) {
            log.error("Failed to load bank accounts from {}: {}", jsonPath, e.getMessage());
        }
        return index;
    }

    /**
     * Looks up the QBO ClassRef for the given storeId.
     * Matches Class entries whose Name starts with the storeId (e.g. "1174 (Mchenry ave)").
     *
     * @return String[2] — [classId, className]; falls back to ["", storeId] if not found
     */
    private String[] lookupClassRef(String storeId) {
        if (classRefIndex == null) {
            classRefIndex = loadClassRefIndex(CLASSREFS_JSON);
        }
        String[] entry = classRefIndex.get(storeId);
        if (entry == null) {
            log.warn("No class ref found for storeId={} in {}", storeId, CLASSREFS_JSON);
            return new String[]{"", storeId};
        }
        return entry;
    }

    /**
     * Parses the static class refs JSON and builds a storeId → [classId, className] index.
     * Matches entries whose Name begins with the storeId followed by a space or end-of-string.
     */
    private Map<String, String[]> loadClassRefIndex(String jsonPath) {
        Map<String, String[]> index = new HashMap<>();
        try {
            JsonNode root    = objectMapper.readTree(new File(jsonPath));
            JsonNode classes = root.path("QueryResponse").path("Class");
            for (JsonNode cls : classes) {
                String name = cls.path("Name").asText("");
                String id   = cls.path("Id").asText("");
                // Name starts with storeId digits followed by a space, e.g. "1174 (Mchenry ave)"
                int spaceIdx = name.indexOf(' ');
                String prefix = spaceIdx > 0 ? name.substring(0, spaceIdx) : name;
                if (prefix.matches("\\d+")) {
                    index.put(prefix, new String[]{id, name});
                    log.debug("Indexed class ref: storeId={} → id={}, name={}", prefix, id, name);
                }
            }
            log.info("Loaded {} store class refs from {}", index.size(), jsonPath);
        } catch (Exception e) {
            log.error("Failed to load class refs from {}: {}", jsonPath, e.getMessage());
        }
        return index;
    }

    /**
     * Looks up the ACCOUNTABLE CASH line from the invoices JSON for the given DocNumber.
     *
     * @param invoicesJson path to the invoices JSON file
     * @param docNumber    e.g. "260301-1174"
     * @return InvoiceLineRef or null if not found
     */
    private InvoiceLineRef lookupInvoiceLine(String invoicesJson, String docNumber) {
        if (!invoicesJson.equals(loadedInvoicesPath) || invoiceIndex == null) {
            invoiceIndex      = loadInvoiceIndex(invoicesJson);
            loadedInvoicesPath = invoicesJson;
        }
        InvoiceLineRef ref = invoiceIndex.get(docNumber);
        if (ref == null) {
            log.warn("No invoice found for docNumber={} in {}", docNumber, invoicesJson);
        }
        return ref;
    }

    /**
     * Parses the invoices JSON and builds a docNumber → InvoiceLineRef index.
     * Only indexes invoices that have a Line with Description = "ACCOUNTABLE CASH".
     */
    private Map<String, InvoiceLineRef> loadInvoiceIndex(String jsonPath) {
        Map<String, InvoiceLineRef> index = new HashMap<>();
        try {
            JsonNode root     = objectMapper.readTree(new File(jsonPath));
            JsonNode invoices = root.path("QueryResponse").path("Invoice");
            for (JsonNode invoice : invoices) {
                String invoiceId = invoice.path("Id").asText("");
                String docNumber = invoice.path("DocNumber").asText("");
                for (JsonNode line : invoice.path("Line")) {
                    if ("ACCOUNTABLE CASH".equalsIgnoreCase(line.path("Description").asText(""))) {
                        String     lineId = line.path("Id").asText("");
                        BigDecimal amount = new BigDecimal(line.path("Amount").asText("0"));
                        index.put(docNumber, new InvoiceLineRef(invoiceId, lineId, amount));
                        log.debug("Indexed invoice: docNumber={} → invoiceId={}, lineId={}, amount={}",
                                docNumber, invoiceId, lineId, amount);
                        break;
                    }
                }
            }
            log.info("Loaded {} invoice ACCOUNTABLE CASH lines from {}", index.size(), jsonPath);
        } catch (Exception e) {
            log.error("Failed to load invoices from {}: {}", jsonPath, e.getMessage());
        }
        return index;
    }

    /** Extract the trailing digits from an account number like "*****1729" → "1729". */
    private String extractLast4(String accountNum) {
        String digits = accountNum.replaceAll("[^0-9]", "");
        return digits.length() >= 4 ? digits.substring(digits.length() - 4) : digits;
    }

    /** Find the index of a column header (case-insensitive, stripped of quotes). */
    private int indexOf(String[] headers, String name) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(name)) return i;
        }
        throw new IllegalArgumentException("CSV column not found: " + name);
    }

    /** Minimal CSV line parser that handles double-quoted fields. */
    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        fields.add(sb.toString());
        return fields.toArray(new String[0]);
    }
}
