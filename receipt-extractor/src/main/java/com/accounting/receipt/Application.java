package com.accounting.receipt;

import com.accounting.receipt.config.AppConfig;
import com.accounting.receipt.pipeline.ReceiptProcessingPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application entry point.
 *
 * Run with: java -jar receipt-extractor-1.0.0.jar
 *
 * On first run, a browser window will open for Gmail OAuth consent.
 * The token is cached locally so subsequent runs are silent.
 */
public class Application {

    private static final Logger log = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        log.info("========================================");
        log.info("  Cash Deposit Receipt Extractor v1.0  ");
        log.info("========================================");

        try {
            AppConfig config = AppConfig.load();
            ReceiptProcessingPipeline pipeline = ReceiptProcessingPipeline.create(config);
            int totalRecords = pipeline.process();
            log.info("Done. {} deposit record(s) extracted and saved.", totalRecords);
        } catch (Exception e) {
            log.error("Fatal error: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
