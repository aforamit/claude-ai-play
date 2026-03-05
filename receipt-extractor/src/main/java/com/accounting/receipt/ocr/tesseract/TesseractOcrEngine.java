package com.accounting.receipt.ocr.tesseract;

import com.accounting.receipt.config.AppConfig;
import com.accounting.receipt.model.DepositRecord;
import com.accounting.receipt.ocr.OcrEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Tesseract (local, offline) OCR engine — STUB / EXTENSION POINT.
 *
 * To implement:
 *   1. Install Tesseract on Windows:
 *      https://github.com/UB-Mannheim/tesseract/wiki
 *
 *   2. Add to pom.xml:
 *        <dependency>
 *            <groupId>net.sourceforge.tess4j</groupId>
 *            <artifactId>tess4j</artifactId>
 *            <version>5.x.x</version>
 *        </dependency>
 *
 *   3. Implement extractDepositRecords() using Tesseract.doOCR() to get raw text,
 *      then write a regex/parser to extract StoreId, Date, Amount from the text.
 *
 *   4. Set ocr.engine=tesseract and ocr.tesseract.data-path=<tessdata folder path>
 *      in application.properties.
 *
 * Note: Tesseract works offline but requires careful regex tuning per receipt format.
 *       It is less accurate than cloud vision APIs for complex or hand-written receipts.
 */
public class TesseractOcrEngine implements OcrEngine {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcrEngine.class);

    public TesseractOcrEngine(AppConfig config) {
        log.warn("Tesseract OCR engine is a stub. Implement extractDepositRecords() to activate.");
    }

    @Override
    public String getEngineName() {
        return "Tesseract OCR (stub)";
    }

    @Override
    public List<DepositRecord> extractDepositRecords(byte[] imageData, String mimeType) {
        throw new UnsupportedOperationException(
                "Tesseract OCR engine not yet implemented. "
                + "Set ocr.engine=claude in application.properties to use the Claude engine.");
    }
}
