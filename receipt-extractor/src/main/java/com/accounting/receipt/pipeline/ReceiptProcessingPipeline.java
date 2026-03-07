package com.accounting.receipt.pipeline;

import com.accounting.receipt.config.AppConfig;
import com.accounting.receipt.email.EmailMessage;
import com.accounting.receipt.email.EmailService;
import com.accounting.receipt.email.ImageAttachment;
import com.accounting.receipt.email.gmail.GmailEmailService;
import com.accounting.receipt.export.DataExporter;
import com.accounting.receipt.export.csv.CsvDataExporter;
import com.accounting.receipt.model.DepositRecord;
import com.accounting.receipt.ocr.OcrEngine;
import com.accounting.receipt.ocr.OcrEngineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the full extraction pipeline:
 *
 *   Gmail → image attachments → OCR engine → deposit records → CSV
 *
 * The three collaborators (EmailService, OcrEngine, DataExporter) are injected,
 * so each can be swapped independently or replaced with mocks in tests.
 */
public class ReceiptProcessingPipeline {

    private static final Logger log = LoggerFactory.getLogger(ReceiptProcessingPipeline.class);

    private final EmailService emailService;
    private final OcrEngine    ocrEngine;
    private final DataExporter exporter;
    private final int          maxEmails;
    private final boolean      markAsRead;

    /**
     * Package-private constructor used directly in unit tests with mock collaborators.
     */
    ReceiptProcessingPipeline(
            EmailService emailService,
            OcrEngine    ocrEngine,
            DataExporter exporter,
            int          maxEmails,
            boolean      markAsRead) {

        this.emailService = emailService;
        this.ocrEngine    = ocrEngine;
        this.exporter     = exporter;
        this.maxEmails    = maxEmails;
        this.markAsRead   = markAsRead;
    }

    /**
     * Production factory — wires all real collaborators from config.
     */
    public static ReceiptProcessingPipeline create(AppConfig config) throws Exception {
        EmailService emailService = GmailEmailService.create(config);
        OcrEngine    ocrEngine    = OcrEngineFactory.create(config);
        DataExporter exporter     = new CsvDataExporter(config);
        int          maxEmails    = config.getInt("email.max-fetch", 10);
        boolean      markAsRead   = config.getBoolean("email.mark-as-read", true);

        log.info("Pipeline ready:");
        log.info("  OCR Engine : {}", ocrEngine.getEngineName());
        log.info("  CSV Output : {}", exporter.getDestinationDescription());
        log.info("  Max Emails : {}", maxEmails);
        log.info("  Mark Read  : {}", markAsRead);

        return new ReceiptProcessingPipeline(emailService, ocrEngine, exporter, maxEmails, markAsRead);
    }

    /**
     * Runs the pipeline end-to-end.
     *
     * @return total number of deposit records extracted and exported
     */
    public int process() throws Exception {

        // Step 1 — Fetch emails
        List<EmailMessage> emails = emailService.fetchLatestUnreadWithImages(maxEmails);
        if (emails.isEmpty()) {
            log.info("No unread emails with receipt images found. Nothing to do.");
            return 0;
        }

        // Step 2 — Extract records from every image in every email
        List<DepositRecord> allRecords = new ArrayList<>();

        for (EmailMessage email : emails) {
            log.info("Processing: '{}' from {} ({})",
                    email.subject(), email.from(), email.receivedDate());

            List<DepositRecord> emailRecords = extractFromEmail(email);
            allRecords.addAll(emailRecords);

            // Step 3 — Mark as read so we don't re-process on the next run
            if (markAsRead && !emailRecords.isEmpty()) {
                try {
                    emailService.markAsRead(email.id());
                } catch (Exception e) {
                    log.warn("Could not mark email {} as read: {}", email.id(), e.getMessage());
                }
            }
        }

        // Step 4 — Export all collected records
        if (allRecords.isEmpty()) {
            log.info("No deposit records could be extracted from the processed emails.");
            return 0;
        }

        int exported = exporter.export(allRecords);

        log.info("─────────────────────────────────────");
        log.info("Emails processed : {}", emails.size());
        log.info("Records extracted: {}", allRecords.size());
        log.info("Records written  : {}", exported);
        log.info("Output file      : {}", exporter.getDestinationDescription());
        log.info("─────────────────────────────────────");
        logSummaryTable(allRecords);

        return allRecords.size();
    }

    /** Logs a grouped summary table: one row per store+deposit-date combination. */
    private void logSummaryTable(List<DepositRecord> records) {
        // Group by storeId + cashDepositDate to preserve insertion order
        Map<String, List<DepositRecord>> grouped = new LinkedHashMap<>();
        for (DepositRecord r : records) {
            grouped.computeIfAbsent(r.storeId() + "|" + r.cashDepositDate(), k -> new ArrayList<>()).add(r);
        }

        log.info("  {}", String.format("%-6s  %7s  %-12s  %s", "Store", "Records", "Deposit Date", "Total Cash Deposit"));
        log.info("  {}", "─".repeat(46));
        for (List<DepositRecord> group : grouped.values()) {
            DepositRecord first = group.get(0);
            log.info("  {}", String.format("%-6s  %7d  %-12s  $%,.2f",
                    first.storeId(), group.size(), first.cashDepositDate(), first.totalCashDeposit()));
        }
    }

    /** Processes all image attachments in a single email and returns the combined records. */
    private List<DepositRecord> extractFromEmail(EmailMessage email) {
        List<DepositRecord> records = new ArrayList<>();

        for (ImageAttachment attachment : email.imageAttachments()) {
            log.info("  Attachment: {} ({})", attachment.filename(), attachment.mimeType());
            try {
                // Pass subject, sender, and received date as context so Claude can resolve store IDs and validate years
                String emailContext = "Subject: " + email.subject()
                        + " | From: " + email.from()
                        + " | Received: " + email.receivedDate();
                List<DepositRecord> extracted =
                        ocrEngine.extractDepositRecords(attachment.data(), attachment.mimeType(), emailContext);

                if (extracted.isEmpty()) {
                    log.warn("  No records found in: {}", attachment.filename());
                } else {
                    extracted.forEach(r -> log.info("  -> {}", r.toDisplayString()));
                    records.addAll(extracted);
                }
            } catch (Exception e) {
                log.error("  Failed to process {}: {}", attachment.filename(), e.getMessage());
                // Continue with remaining attachments
            }
        }

        return records;
    }
}
