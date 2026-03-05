package com.accounting.receipt.ocr;

import com.accounting.receipt.config.AppConfig;
import com.accounting.receipt.ocr.claude.ClaudeVisionOcrEngine;
import com.accounting.receipt.ocr.googlevision.GoogleVisionOcrEngine;
import com.accounting.receipt.ocr.tesseract.TesseractOcrEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates the configured {@link OcrEngine} implementation.
 *
 * Controlled by {@code ocr.engine} in application.properties.
 * Supported values: {@code claude} (default), {@code google-vision}, {@code tesseract}.
 */
public class OcrEngineFactory {

    private static final Logger log = LoggerFactory.getLogger(OcrEngineFactory.class);

    public static OcrEngine create(AppConfig config) {
        String engine = config.get("ocr.engine", "claude").trim().toLowerCase();
        log.info("OCR engine selected: {}", engine);

        return switch (engine) {
            case "claude"        -> new ClaudeVisionOcrEngine(config);
            case "google-vision" -> new GoogleVisionOcrEngine(config);
            case "tesseract"     -> new TesseractOcrEngine(config);
            default -> throw new IllegalArgumentException(
                    "Unknown ocr.engine='" + engine + "'. Valid options: claude, google-vision, tesseract");
        };
    }
}
