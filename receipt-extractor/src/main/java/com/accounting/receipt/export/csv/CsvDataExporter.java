package com.accounting.receipt.export.csv;

import com.accounting.receipt.config.AppConfig;
import com.accounting.receipt.export.DataExporter;
import com.accounting.receipt.model.DepositRecord;
import com.opencsv.CSVWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Writes deposit records to a CSV file.
 *
 * Configuration keys (application.properties):
 *   output.csv.path   = deposits.csv    (relative or absolute path)
 *   output.csv.append = true            (true = append; false = overwrite each run)
 *
 * Output columns: Store ID | Cash Deposit Date (MM-DD-YYYY) | Cash Balance Date (MM-DD-YYYY) | Amount ($)
 */
public class CsvDataExporter implements DataExporter {

    private static final Logger log = LoggerFactory.getLogger(CsvDataExporter.class);

    private static final String[] HEADER = {
            "Store ID",
            "Deposit Account #",
            "Cash Deposit Date (MM-DD-YYYY)",
            "Cash Balance Date (MM-DD-YYYY)",
            "Amount ($)",
            "Total Cash Deposit ($)"
    };

    private final Path outputPath;
    private final boolean appendMode;

    public CsvDataExporter(AppConfig config) {
        this.outputPath = Paths.get(config.get("output.csv.path", "deposits.csv"));
        this.appendMode = config.getBoolean("output.csv.append", true);
    }

    @Override
    public String getDestinationDescription() {
        return outputPath.toAbsolutePath().toString();
    }

    @Override
    public int export(List<DepositRecord> records) throws Exception {
        if (records == null || records.isEmpty()) {
            log.info("No records to export.");
            return 0;
        }

        // Create parent directories if needed
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }

        boolean fileAlreadyHasContent = Files.exists(outputPath) && Files.size(outputPath) > 0;
        boolean writeHeader = !appendMode || !fileAlreadyHasContent;

        try (CSVWriter writer = new CSVWriter(
                new FileWriter(outputPath.toFile(), appendMode),
                CSVWriter.DEFAULT_SEPARATOR,
                CSVWriter.DEFAULT_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                CSVWriter.DEFAULT_LINE_END)) {

            if (writeHeader) {
                writer.writeNext(HEADER);
            }

            for (DepositRecord r : records) {
                writer.writeNext(new String[]{
                        r.storeId(),
                        r.depositAccountNumber() != null ? r.depositAccountNumber() : "",
                        r.cashDepositDate(),
                        r.cashBalanceDate()      != null ? r.cashBalanceDate()      : "",
                        String.format("%.2f", r.amount()),
                        String.format("%.2f", r.totalCashDeposit())
                });
            }
        }

        log.info("Exported {} record(s) to: {}", records.size(), outputPath.toAbsolutePath());
        return records.size();
    }
}
