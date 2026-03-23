# Cash Deposit Receipt Extractor — Engineering Guide

## Project Purpose

Automates reading of cash deposit receipts emailed as JPEG images from store managers to an accounting firm's Gmail account. Extracts structured deposit data and writes it to a CSV for downstream use (e.g. QuickBooks reconciliation).

---

## Architecture

```
Gmail API
   │
   ▼
GmailEmailService          (EmailService interface)
   │  fetches unread emails with image attachments
   ▼
ReceiptProcessingPipeline  (orchestrator)
   │  passes image + email context to OCR engine
   ▼
ClaudeVisionOcrEngine      (OcrEngine interface)
   │  compresses image if needed, calls Claude API,
   │  parses JSON response → List<DepositRecord>
   ▼
CsvDataExporter            (DataExporter interface)
   │  appends records to deposits.csv
   ▼
deposits.csv
```

All three collaborators (EmailService, OcrEngine, DataExporter) are injected into the pipeline, so each can be swapped or mocked independently.

---

## Data Model

`DepositRecord` — one row per deposit line extracted from a receipt:

| Field                  | Type   | Description |
|------------------------|--------|-------------|
| `storeId`              | String | 4-digit store number (e.g. "1176") |
| `cashDepositDate`      | String | Printed deposit date on the receipt — MM-DD-YYYY |
| `cashBalanceDate`      | String | Hand-written balance/verification date — MM-DD-YYYY; blank if absent |
| `depositAccountNumber` | String | Masked bank account number as printed (e.g. "*****1234"); blank if absent |
| `totalCashDeposit`     | double | "Original Deposit" total from the receipt |
| `amount`               | double | Individual daily row amount |

**Two receipt layouts are supported:**
- **Single receipt with daily breakdown table** — multiple rows share one `totalCashDeposit` (the receipt total); each row has its own `cashBalanceDate` and `amount`.
- **Multiple separate transaction receipts** — each receipt has its own `totalCashDeposit`.

---

## Key Components

### `ClaudeVisionOcrEngine`

The primary OCR implementation, backed by the Anthropic Claude Vision API.

**Image compression (two-pass)**

Claude's API enforces a 5 MB limit on base64-encoded image data (~3.7 MB raw). Images above the threshold are automatically compressed before sending:

- **Pass 1** — JPEG re-encode at quality 0.92 with no downscaling. Preserves full resolution for best OCR accuracy.
- **Pass 2** — If Pass 1 result is still too large, proportional downscale + JPEG encode at 0.85. Fallback for very large images.

**Email context enrichment**

The pipeline passes `Subject | From | Received` as a context hint alongside the image. The prompt instructs Claude to:
- Extract the store ID from the email subject if it cannot be read from the receipt.
- Use the `Received` date as authoritative ground truth for the year — corrects cases where the printed year on a receipt is ambiguous or OCR misreads `2026` as `2025` on a compressed image.

**Date handling**

- `normalizeDate()` — parses 8+ date format variants (MM-dd-yyyy, MM/dd/yyyy, yyyy-MM-dd, etc.) and normalises to `MM-DD-YYYY`.
- `inferBalanceDateYear()` — if Claude returns a yearless balance date (e.g. `"02-07"`), fills in the year from `cashDepositDate`.
- Prompt instructs Claude to do the same year inference itself before responding.

**Response robustness**
- Strips markdown code fences if Claude wraps JSON output in them.
- `extractStoreId()` — strips non-numeric prefixes ("Store ", "STR-") and validates 4-digit format.
- `extractAccountNumber()` — normalises bare 4-digit values to `*****XXXX` format.
- Malformed individual records are skipped with a warning; the rest still proceed.
- Non-receipt images (logos, donation emails, etc.) are detected by the prompt and return `[]` with a warning.

### `ReceiptProcessingPipeline`

Orchestrates the end-to-end flow: fetch → extract → export → mark as read.

- Emails with no extractable records are **not** marked as read (they remain unread for investigation).
- Per-attachment errors are logged and skipped; processing continues with remaining attachments and emails.
- Logs a grouped summary table (store × deposit date) after each run.

### `GmailEmailService`

- OAuth 2.0 browser-based auth on first run; token cached in `tokens/StoredCredential`.
- If the token expires or is revoked, delete `tokens/StoredCredential` and re-run to re-authenticate.
- Transient Gmail API 500 errors on individual messages are logged and skipped; the email remains unread.

---

## Configuration (`application.properties`)

| Property                  | Default            | Description |
|---------------------------|--------------------|-------------|
| `ocr.engine`              | `claude`           | Active OCR engine: `claude` \| `google-vision` \| `tesseract` |
| `ocr.claude.api-key`      | —                  | Anthropic API key (or set `ANTHROPIC_API_KEY` env var) |
| `ocr.claude.model`        | `claude-opus-4-6`  | Claude model ID |
| `ocr.claude.max-tokens`   | `2048`             | Max tokens in Claude's response |
| `ocr.claude.max-image-mb` | `10`               | Soft cap for image size before compression kicks in |
| `gmail.credentials.path`  | `credentials.json` | Path to Google OAuth client credentials file |
| `gmail.tokens.path`       | `tokens`           | Directory for cached OAuth token |
| `gmail.oauth.port`        | `8888`             | Local redirect port for OAuth browser flow |
| `email.max-fetch`         | `10`               | Max unread emails to process per run |
| `email.mark-as-read`      | `true`             | Mark emails read after successful extraction |
| `output.csv.path`         | `deposits.csv`     | Output CSV file path |
| `output.csv.append`       | `true`             | `true` = append; `false` = overwrite each run |

---

## Extending the Application

### Add a new OCR engine

1. Implement `OcrEngine` (two methods: `getEngineName()`, `extractDepositRecords(...)`).
2. Register it in `OcrEngineFactory.create()`.
3. Set `ocr.engine=<your-key>` in `application.properties`.

The `OcrEngine` interface has two overloads:
```java
List<DepositRecord> extractDepositRecords(byte[] imageData, String mimeType);
List<DepositRecord> extractDepositRecords(byte[] imageData, String mimeType, String emailContext);
```
The default implementation of the second delegates to the first, so simple engines only need to implement one method.

### Add a new email source

Implement `EmailService` and wire it in the pipeline factory (`ReceiptProcessingPipeline.create()`).

### Add a new export target

Implement `DataExporter` and wire it in the pipeline factory.

---

## Build & Run

```bash
# Build
mvn package -DskipTests

# Run (Java 21 required)
java -jar target/receipt-extractor-1.0.0.jar
```

On first run, a browser window will open for Gmail OAuth. After approval, the token is cached and subsequent runs are fully unattended.

**Windows convenience scripts** (use the local Node 18 path if needed):
```
run.cmd    — build + run
```

---

## Output CSV Format

```
storeId,cashDepositDate,cashBalanceDate,depositAccountNumber,totalCashDeposit,amount
1177,03-06-2026,03-02-2026,*****2776,286.20,286.20
```

New records are appended on each run (`output.csv.append=true`). Set to `false` to overwrite.
