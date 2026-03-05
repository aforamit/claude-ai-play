package com.accounting.receipt.ocr;

import com.accounting.receipt.model.DepositRecord;

import java.util.List;

/**
 * Strategy interface for OCR / AI-vision extraction.
 *
 * To add a new engine:
 *   1. Create a class implementing this interface.
 *   2. Register it in {@link OcrEngineFactory}.
 *   3. Set {@code ocr.engine=<name>} in application.properties.
 */
public interface OcrEngine {

    /**
     * Analyses an image and returns every deposit record found in it.
     *
     * A single image may contain:
     *   - One receipt with an aggregated table broken down by date.
     *   - Multiple individual receipts side-by-side.
     *
     * @param imageData raw bytes of the image
     * @param mimeType  e.g. "image/jpeg", "image/png"
     * @return list of extracted records; empty if none found
     */
    List<DepositRecord> extractDepositRecords(byte[] imageData, String mimeType) throws Exception;

    /**
     * Variant with email context — passes subject/sender as hints to help identify the store.
     * Default delegates to the context-free variant; engines that support it should override.
     *
     * @param emailContext additional text from the email (e.g. subject line)
     */
    default List<DepositRecord> extractDepositRecords(byte[] imageData, String mimeType, String emailContext)
            throws Exception {
        return extractDepositRecords(imageData, mimeType);
    }

    /** Human-readable name used in logs (e.g. "Claude Vision (claude-opus-4-6)"). */
    String getEngineName();
}
