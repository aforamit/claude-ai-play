package com.accounting.qbo.service.impl;

import com.accounting.qbo.dto.MatchedTransaction;
import com.accounting.qbo.model.Deposit;
import com.accounting.qbo.model.DepositLine;
import com.accounting.qbo.model.Invoice;
import com.accounting.qbo.model.InvoiceLine;
import com.accounting.qbo.service.ReconciliationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReconciliationServiceImpl implements ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationServiceImpl.class);
    private static final String ACCOUNTABLE_CASH = "ACCOUNTABLE CASH";

    private static final String CSV_HEADER =
            "InvoiceId,InvoiceDocNumber,InvoiceTxnDate,InvoiceDueDate," +
            "InvoiceCustomer,InvoiceLocation,InvoiceAccountableCash," +
            "DepositId,DepositTxnDate,DepositTotalAmount,DepositAccount,AccountableCashAmount";

    private final ObjectMapper objectMapper;

    public ReconciliationServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public List<MatchedTransaction> reconcile(String invoiceFile, String depositFile, String csvOutputPath) {
        Map<String, Invoice> invoiceMap = loadInvoices(invoiceFile);
        List<Deposit> deposits = loadDeposits(depositFile);

        log.info("Loaded {} invoices and {} deposits", invoiceMap.size(), deposits.size());

        List<MatchedTransaction> matches = match(invoiceMap, deposits);

        log.info("Found {} matched transactions", matches.size());

        writeCsv(matches, csvOutputPath);

        return matches;
    }

    // ── Matching logic ────────────────────────────────────────────────────────

    private List<MatchedTransaction> match(Map<String, Invoice> invoiceMap, List<Deposit> deposits) {
        List<MatchedTransaction> results = new ArrayList<>();

        for (Deposit deposit : deposits) {
            if (deposit.getLines() == null) continue;

            for (DepositLine line : deposit.getLines()) {
                if (!ACCOUNTABLE_CASH.equalsIgnoreCase(line.getDescription())) continue;
                if (line.getLinkedTransactions() == null) continue;

                for (DepositLine.LinkedTransaction link : line.getLinkedTransactions()) {
                    String txnId = link.getTxnId();
                    Invoice invoice = invoiceMap.get(txnId);

                    if (invoice == null) {
                        log.debug("No invoice found for TxnId={} in deposit={}", txnId, deposit.getId());
                        continue;
                    }

                    results.add(buildMatch(invoice, deposit, line));
                }
            }
        }

        return results;
    }

    private MatchedTransaction buildMatch(Invoice invoice, Deposit deposit, DepositLine matchedLine) {
        MatchedTransaction m = new MatchedTransaction();

        // Invoice fields
        m.setInvoiceId(invoice.getId());
        m.setInvoiceDocNumber(invoice.getDocNumber());
        m.setInvoiceTxnDate(invoice.getTxnDate());
        m.setInvoiceDueDate(invoice.getDueDate());
        m.setInvoiceCustomer(invoice.getCustomerRef() != null ? invoice.getCustomerRef().getName() : null);
        m.setInvoiceLocation(resolveLocation(invoice));
        m.setInvoiceAccountableCash(resolveAccountableCash(invoice));

        // Deposit fields
        m.setDepositId(deposit.getId());
        m.setDepositTxnDate(deposit.getTxnDate());
        m.setDepositTotalAmount(deposit.getTotalAmount());
        m.setDepositAccount(deposit.getDepositToAccountRef() != null ? deposit.getDepositToAccountRef().getName() : null);
        m.setAccountableCashAmount(matchedLine.getAmount());

        return m;
    }

    /**
     * Extracts the Amount from the invoice line where Description == "ACCOUNTABLE CASH".
     */
    private BigDecimal resolveAccountableCash(Invoice invoice) {
        if (invoice.getLines() == null) return null;
        return invoice.getLines().stream()
                .filter(l -> ACCOUNTABLE_CASH.equalsIgnoreCase(l.getDescription()))
                .map(InvoiceLine::getAmount)
                .findFirst()
                .orElse(null);
    }

    /**
     * Extracts the location (ClassRef.name) from the ACCOUNTABLE CASH line of the invoice,
     * falling back to the first line that has a ClassRef.
     */
    private String resolveLocation(Invoice invoice) {
        if (invoice.getLines() == null) return null;

        return invoice.getLines().stream()
                .filter(l -> ACCOUNTABLE_CASH.equalsIgnoreCase(l.getDescription()))
                .findFirst()
                .map(this::classRefName)
                .orElseGet(() -> invoice.getLines().stream()
                        .map(this::classRefName)
                        .filter(n -> n != null && !n.isBlank())
                        .findFirst()
                        .orElse(null));
    }

    private String classRefName(InvoiceLine line) {
        if (line.getSalesItemDetail() == null) return null;
        if (line.getSalesItemDetail().getClassRef() == null) return null;
        return line.getSalesItemDetail().getClassRef().getName();
    }

    // ── JSON loading ──────────────────────────────────────────────────────────

    private Map<String, Invoice> loadInvoices(String filePath) {
        try {
            String json = Files.readString(Path.of(filePath));
            JsonNode root = objectMapper.readTree(json);
            JsonNode invoiceArray = root.path("QueryResponse").path("Invoice");

            List<Invoice> invoices = new ArrayList<>();
            for (JsonNode node : invoiceArray) {
                invoices.add(objectMapper.treeToValue(node, Invoice.class));
            }

            return invoices.stream().collect(Collectors.toMap(Invoice::getId, Function.identity()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load invoices from: " + filePath, e);
        }
    }

    private List<Deposit> loadDeposits(String filePath) {
        try {
            String json = Files.readString(Path.of(filePath));
            JsonNode root = objectMapper.readTree(json);
            JsonNode depositArray = root.path("QueryResponse").path("Deposit");

            List<Deposit> deposits = new ArrayList<>();
            for (JsonNode node : depositArray) {
                deposits.add(objectMapper.treeToValue(node, Deposit.class));
            }

            return deposits;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load deposits from: " + filePath, e);
        }
    }

    // ── CSV export ────────────────────────────────────────────────────────────

    private void writeCsv(List<MatchedTransaction> matches, String csvOutputPath) {
        try {
            Path csvPath = Path.of(csvOutputPath);
            Files.createDirectories(csvPath.getParent());

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(csvPath.toFile()))) {
                writer.write(CSV_HEADER);
                writer.newLine();

                for (MatchedTransaction m : matches) {
                    writer.write(buildCsvRow(m));
                    writer.newLine();
                }
            }

            log.info("CSV written to: {}", csvOutputPath);
        } catch (IOException e) {
            log.error("Failed to write CSV to {}: {}", csvOutputPath, e.getMessage());
            throw new RuntimeException("Failed to write CSV: " + e.getMessage(), e);
        }
    }

    private String buildCsvRow(MatchedTransaction m) {
        return String.join(",",
                safe(m.getInvoiceId()),
                safe(m.getInvoiceDocNumber()),
                safe(m.getInvoiceTxnDate()),
                safe(m.getInvoiceDueDate()),
                csvQuote(m.getInvoiceCustomer()),
                csvQuote(m.getInvoiceLocation()),
                safe(m.getInvoiceAccountableCash()),
                safe(m.getDepositId()),
                safe(m.getDepositTxnDate()),
                safe(m.getDepositTotalAmount()),
                csvQuote(m.getDepositAccount()),
                safe(m.getAccountableCashAmount())
        );
    }

    private String safe(Object value) {
        return value == null ? "" : value.toString();
    }

    /** Wraps value in quotes and escapes embedded quotes — handles names with commas. */
    private String csvQuote(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
