package com.accounting.receipt.ocr.claude;

import com.accounting.receipt.config.AppConfig;
import com.accounting.receipt.model.DepositRecord;
import com.accounting.receipt.ocr.OcrEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * OCR engine backed by Anthropic's Claude Vision API.
 *
 * Features:
 *   - Sends image as base64 with a structured extraction prompt.
 *   - Passes email subject as a context hint to resolve store IDs.
 *   - Auto-compresses images that exceed the 5 MB API limit.
 *   - Returns empty list for non-receipt images (e.g. logos, icons).
 *
 * Configuration keys (application.properties):
 *   ocr.engine              = claude
 *   ocr.claude.api-key      = sk-ant-...   (or env: ANTHROPIC_API_KEY)
 *   ocr.claude.model        = claude-opus-4-6
 *   ocr.claude.max-tokens   = 2048
 */
public class ClaudeVisionOcrEngine implements OcrEngine {

    private static final Logger log = LoggerFactory.getLogger(ClaudeVisionOcrEngine.class);

    private static final String API_URL           = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final DateTimeFormatter OUT_FMT = DateTimeFormatter.ofPattern("MM-dd-yyyy");

    /** Claude API hard limit for image data (raw bytes). Base64 adds ~33% overhead. */
    private static final long API_RAW_LIMIT_BYTES = 5L * 1024 * 1024;

    /** Compress when estimated base64 payload would exceed the API limit (safe headroom). */
    private static final long COMPRESS_THRESHOLD = (long)(API_RAW_LIMIT_BYTES / 1.34);

    private final String     apiKey;
    private final String     model;
    private final int        maxTokens;
    private final HttpClient http;
    private final ObjectMapper json;

    public ClaudeVisionOcrEngine(AppConfig config) {
        this.apiKey        = config.get("ocr.claude.api-key");
        this.model         = config.get("ocr.claude.model", "claude-opus-4-6");
        this.maxTokens     = config.getInt("ocr.claude.max-tokens", 2048);
        this.http          = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.json          = new ObjectMapper();

        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("YOUR_")) {
            throw new IllegalStateException(
                    "Anthropic API key not configured.\n"
                    + "Set ocr.claude.api-key in application.properties, or export ANTHROPIC_API_KEY.");
        }
    }

    @Override
    public String getEngineName() {
        return "Claude Vision (" + model + ")";
    }

    @Override
    public List<DepositRecord> extractDepositRecords(byte[] imageData, String mimeType) throws Exception {
        return extractDepositRecords(imageData, mimeType, null);
    }

    @Override
    public List<DepositRecord> extractDepositRecords(byte[] imageData, String mimeType, String emailContext)
            throws Exception {

        // Compress if above the size threshold
        byte[] data = maybeCompress(imageData, mimeType);
        log.debug("Sending {}KB image ({}) to Claude...", data.length / 1024, mimeType);

        String body = buildRequestJson(data, mimeType, emailContext);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type",      "application/json")
                .header("x-api-key",         apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(120))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Claude API error " + response.statusCode() + ": " + response.body());
        }

        return parseResponse(response.body());
    }

    // -------------------------------------------------------------------------
    // Image compression
    // -------------------------------------------------------------------------

    /**
     * Compresses the image when its base64-encoded size would exceed the Claude API's 5 MB limit.
     * Base64 encoding inflates raw bytes by ~33%, so the threshold is ~3.7 MB of raw data.
     */
    private byte[] maybeCompress(byte[] imageData, String mimeType) throws Exception {
        if (imageData.length <= COMPRESS_THRESHOLD) {
            return imageData;
        }

        log.info("Image {}KB would exceed API limit after base64 encoding — compressing...",
                imageData.length / 1024);

        BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageData));
        if (original == null) {
            log.warn("Could not decode image for compression — sending original.");
            return imageData;
        }

        // Scale proportionally so the compressed file stays within the threshold
        double scale = Math.sqrt((double) COMPRESS_THRESHOLD / imageData.length) * 0.95;
        int newW = Math.max(1, (int)(original.getWidth()  * scale));
        int newH = Math.max(1, (int)(original.getHeight() * scale));

        BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original.getScaledInstance(newW, newH, Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // Always write as JPEG for maximum size reduction
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam params = writer.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(0.85f);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(scaled, null, null), params);
        }
        writer.dispose();

        byte[] compressed = baos.toByteArray();
        log.info("Compressed {}KB → {}KB", imageData.length / 1024, compressed.length / 1024);
        return compressed;
    }

    // -------------------------------------------------------------------------
    // Request building
    // -------------------------------------------------------------------------

    private String buildRequestJson(byte[] imageData, String mimeType, String emailContext) throws Exception {
        // After compression we always write JPEG; update the mime type accordingly
        String effectiveMime = (imageData.length <= COMPRESS_THRESHOLD) ? mimeType : "image/jpeg";

        String base64 = Base64.getEncoder().encodeToString(imageData);

        ObjectNode root = json.createObjectNode();
        root.put("model",      model);
        root.put("max_tokens", maxTokens);

        ArrayNode messages = root.putArray("messages");
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");

        ArrayNode content = userMsg.putArray("content");

        // Image block
        ObjectNode imgBlock = content.addObject();
        imgBlock.put("type", "image");
        ObjectNode source = imgBlock.putObject("source");
        source.put("type",       "base64");
        source.put("media_type", effectiveMime);
        source.put("data",       base64);

        // Text prompt block
        ObjectNode textBlock = content.addObject();
        textBlock.put("type", "text");
        textBlock.put("text", buildPrompt(emailContext));

        return json.writeValueAsString(root);
    }

    private String buildPrompt(String emailContext) {
        String contextHint = (emailContext != null && !emailContext.isBlank())
                ? "\nEmail context (use this to help identify the store if the receipt is unclear): \""
                  + emailContext.replace("\"", "'") + "\"\n"
                : "";

        return """
                You are a data extraction assistant for an accounting firm.
                """ + contextHint + """

                Examine the image carefully.

                IMPORTANT: If the image does NOT contain a bank or store cash deposit receipt
                (e.g. it is a logo, icon, advertisement, hotel booking, or any other non-receipt image),
                respond with exactly: []

                If it IS a cash deposit receipt, extract every deposit record.
                Each record must have:

                  1. storeId               — a 4-digit store number (e.g. "1176", "1174").
                                             Look for it printed or stamped on the receipt.
                                             If not on the receipt, extract the 4-digit number from the email context above.
                                             Use "UNKNOWN" only if truly not found anywhere.

                  2. cashDepositDate        — the PRINTED deposit date on the receipt, in MM-DD-YYYY format.
                                             This is typically typed or pre-printed text.
                                             Use "01-01-1900" only if genuinely unreadable.

                  3. cashBalanceDate        — a HAND-WRITTEN date on the receipt (often written in pen or pencil
                                             as a balance verification or reconciliation date), in MM-DD-YYYY format.
                                             This is a different date from the deposit date and is usually written by hand.
                                             Use "" (empty string) if no hand-written date is present.

                  4. depositAccountNumber   — the masked bank account number printed on the receipt.
                                             It follows the pattern *****XXXX where X is a digit (e.g. "*****1234").
                                             Copy the value exactly as printed, including the asterisks.
                                             Use "" if not visible on the receipt.

                  5. totalCashDeposit       — the "Original Deposit" dollar amount printed on the transaction receipt.
                                             This is a plain number with no $ sign or commas (e.g. 1142.00).
                                             IMPORTANT RULE:
                                             - If the image shows ONE receipt containing a DAILY BREAKDOWN TABLE
                                               (multiple rows, each with its own date and amount), ALL rows share
                                               the SAME totalCashDeposit — the single "Original Deposit" on that receipt.
                                             - If the image shows MULTIPLE SEPARATE transaction receipts,
                                               each receipt has its OWN "Original Deposit" value.
                                             Use 0.0 if not found.

                  6. amount                 — the individual cash deposit amount for this specific row/record.
                                             Plain number, no $ or commas (e.g. 276.00).

                The receipt may show:
                  - A single receipt with a daily breakdown table → extract each row as a separate record,
                    all sharing the same depositAccountNumber and totalCashDeposit from that receipt.
                  - Multiple separate receipts → extract each one with its own values.

                Respond with ONLY a valid JSON array, nothing else:
                [
                  {"storeId": "1176", "cashDepositDate": "02-17-2026", "cashBalanceDate": "02-13-2026", "depositAccountNumber": "*****4321", "totalCashDeposit": 1142.00, "amount": 276.00},
                  {"storeId": "1176", "cashDepositDate": "02-17-2026", "cashBalanceDate": "02-14-2026", "depositAccountNumber": "*****4321", "totalCashDeposit": 1142.00, "amount": 315.00}
                ]
                """;
    }

    // -------------------------------------------------------------------------
    // Response parsing
    // -------------------------------------------------------------------------

    private List<DepositRecord> parseResponse(String responseBody) throws Exception {
        JsonNode root = json.readTree(responseBody);
        JsonNode contentArr = root.path("content");

        if (contentArr.isEmpty()) {
            log.warn("Claude returned empty content.");
            return Collections.emptyList();
        }

        String text = contentArr.get(0).path("text").asText("[]");
        text = extractJsonArray(text);
        log.debug("Claude raw JSON: {}", text);

        JsonNode arr = json.readTree(text);
        List<DepositRecord> records = new ArrayList<>();

        for (JsonNode node : arr) {
            try {
                String storeId              = extractStoreId(node);
                String rawDepositDate       = node.path("cashDepositDate").asText("").trim();
                String cashDepositDate      = rawDepositDate.isBlank() ? "01-01-1900" : normalizeDate(rawDepositDate);
                String cashBalanceDate      = normalizeDate(node.path("cashBalanceDate").asText("").trim());
                String depositAccountNumber = extractAccountNumber(node);
                double totalCashDeposit     = node.path("totalCashDeposit").asDouble(0.0);
                double amount               = node.path("amount").asDouble(0.0);
                records.add(new DepositRecord(storeId, cashDepositDate, cashBalanceDate,
                        depositAccountNumber, totalCashDeposit, amount));
            } catch (Exception e) {
                log.warn("Skipping malformed record {}: {}", node, e.getMessage());
            }
        }

        log.info("Claude extracted {} deposit record(s) from image.", records.size());
        return records;
    }

    /**
     * Extracts the masked account number from a JSON record node.
     * Accepts values like "*****1234" or "1234" and normalises to "*****XXXX" format.
     * Returns empty string if absent or unreadable.
     */
    private String extractAccountNumber(JsonNode node) {
        String raw = node.path("depositAccountNumber").asText("").trim();
        if (raw.isBlank()) return "";
        // If Claude returned only the last 4 digits, prepend the mask
        if (raw.matches("\\d{4}")) return "*****" + raw;
        // Accept any value that already contains the expected pattern
        return raw;
    }

    /**
     * Extracts and normalises the store ID from a JSON record node.
     * Strips non-digit noise and validates the expected 4-digit format.
     */
    private String extractStoreId(JsonNode node) {
        String raw = node.path("storeId").asText("UNKNOWN").trim();
        if (raw.isBlank() || raw.equalsIgnoreCase("UNKNOWN")) return "UNKNOWN";
        // Strip common prefixes like "Store ", "STR-" and keep the numeric part
        String digits = raw.replaceAll("[^0-9]", "");
        if (!digits.isBlank()) return digits;
        return raw; // fall back to whatever Claude returned
    }

    /** Strips markdown code fences if Claude wraps the JSON in them. */
    private String extractJsonArray(String text) {
        text = text.strip();
        int start = text.indexOf('[');
        int end   = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return "[]";
    }

    /**
     * Attempts to parse a date string in several common formats and re-format as MM-DD-YYYY.
     * Returns empty string for blank input (used for optional cashBalanceDate).
     */
    private String normalizeDate(String raw) {
        if (raw == null || raw.isBlank()) return "";

        List<DateTimeFormatter> parsers = List.of(
                DateTimeFormatter.ofPattern("MM-dd-yyyy"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                DateTimeFormatter.ofPattern("M/d/yyyy"),
                DateTimeFormatter.ofPattern("M-d-yyyy"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("MMM dd, yyyy"),
                DateTimeFormatter.ofPattern("MMMM dd, yyyy"),
                DateTimeFormatter.ofPattern("MMM d, yyyy")
        );

        for (DateTimeFormatter fmt : parsers) {
            try {
                return LocalDate.parse(raw.trim(), fmt).format(OUT_FMT);
            } catch (DateTimeParseException ignored) {}
        }

        log.warn("Could not parse date '{}' — keeping as-is.", raw);
        return raw;
    }
}
