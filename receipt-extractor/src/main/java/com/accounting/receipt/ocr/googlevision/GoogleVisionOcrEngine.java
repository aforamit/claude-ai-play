package com.accounting.receipt.ocr.googlevision;

import com.accounting.receipt.config.AppConfig;
import com.accounting.receipt.model.DepositRecord;
import com.accounting.receipt.ocr.OcrEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Google Cloud Vision OCR engine — STUB / EXTENSION POINT.
 *
 * To implement:
 *   1. Add to pom.xml:
 *        <dependency>
 *            <groupId>com.google.cloud</groupId>
 *            <artifactId>google-cloud-vision</artifactId>
 *            <version>3.x.x</version>
 *        </dependency>
 *
 *   2. Set up Application Default Credentials:
 *        gcloud auth application-default login
 *      OR set GOOGLE_APPLICATION_CREDENTIALS env var to a service-account key file.
 *
 *   3. Implement extractDepositRecords() using ImageAnnotatorClient and DOCUMENT_TEXT_DETECTION.
 *      Then parse the raw OCR text into DepositRecord objects.
 *
 *   4. Set ocr.engine=google-vision in application.properties.
 */
public class GoogleVisionOcrEngine implements OcrEngine {

    private static final Logger log = LoggerFactory.getLogger(GoogleVisionOcrEngine.class);

    public GoogleVisionOcrEngine(AppConfig config) {
        log.warn("Google Vision OCR engine is a stub. Implement extractDepositRecords() to activate.");
    }

    @Override
    public String getEngineName() {
        return "Google Cloud Vision (stub)";
    }

    @Override
    public List<DepositRecord> extractDepositRecords(byte[] imageData, String mimeType) {
        throw new UnsupportedOperationException(
                "Google Vision OCR engine not yet implemented. "
                + "Set ocr.engine=claude in application.properties to use the Claude engine.");
    }
}
