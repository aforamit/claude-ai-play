# Cash Deposit Receipt Extractor

Automatically reads cash deposit receipt images emailed from store managers, extracts structured deposit data using Claude Vision AI, and writes it to a CSV file for accounting reconciliation.

## What It Does

1. Connects to your Gmail account and fetches the latest unread emails with image attachments.
2. Sends each image to Claude Vision API with the email subject and received date as context.
3. Extracts per-day deposit records: store ID, deposit date, balance date, account number, and amounts.
4. Appends the records to `deposits.csv`.
5. Marks processed emails as read so they are not re-processed on the next run.

## Extracted Fields

| Field | Description |
|---|---|
| Store ID | 4-digit store number |
| Cash Deposit Date | Printed deposit date on the receipt |
| Cash Balance Date | Hand-written balance/verification date (if present) |
| Account Number | Masked bank account number (e.g. `*****1729`) |
| Total Cash Deposit | "Original Deposit" total from the receipt |
| Amount | Individual daily row amount |

## Prerequisites

- Java 21
- Maven 3.x
- Anthropic API key — [console.anthropic.com](https://console.anthropic.com)
- Google Cloud project with Gmail API enabled and an OAuth 2.0 client credentials file (`credentials.json`)

## Quick Start

**1. Configure**

Edit `application.properties`:

```properties
ocr.claude.api-key=sk-ant-...
gmail.credentials.path=credentials.json
output.csv.path=deposits.csv
```

**2. Build**

```bash
mvn package -DskipTests
```

**3. Run**

```bash
java -jar target/receipt-extractor-1.0.0.jar
```

On first run a browser window opens for Gmail OAuth. After you approve, the token is cached in the `tokens/` folder and subsequent runs are fully unattended.

## Configuration Reference

| Property | Default | Description |
|---|---|---|
| `ocr.engine` | `claude` | OCR engine: `claude` \| `google-vision` \| `tesseract` |
| `ocr.claude.api-key` | — | Anthropic API key |
| `ocr.claude.model` | `claude-opus-4-6` | Claude model to use |
| `email.max-fetch` | `10` | Max unread emails to process per run |
| `email.mark-as-read` | `true` | Mark emails read after extraction |
| `output.csv.path` | `deposits.csv` | Output file path |
| `output.csv.append` | `true` | Append rows (`true`) or overwrite (`false`) each run |

## Receipt Layouts Supported

- **Single receipt with daily breakdown table** — one receipt lists multiple days; all rows share the same deposit total.
- **Multiple separate transaction receipts** — each receipt is its own deposit event with its own total.

Both layouts are detected automatically.

## Re-authenticating Gmail

If you see `invalid_grant: Token has been expired or revoked`, delete the cached token and re-run:

```bash
rm tokens/StoredCredential
java -jar target/receipt-extractor-1.0.0.jar
```

## Project Structure

```
src/main/java/com/accounting/receipt/
├── Application.java                  Entry point
├── config/AppConfig.java             Properties loader
├── email/
│   ├── EmailService.java             Interface — swap email providers here
│   ├── EmailMessage.java             Immutable email + attachments model
│   └── gmail/GmailEmailService.java  Gmail OAuth 2.0 implementation
├── ocr/
│   ├── OcrEngine.java                Interface — swap OCR engines here
│   ├── OcrEngineFactory.java         Selects engine from config
│   ├── claude/ClaudeVisionOcrEngine  Primary engine (image compression + prompt)
│   ├── googlevision/                 Stub — Google Vision alternative
│   └── tesseract/                    Stub — local Tesseract alternative
├── model/DepositRecord.java          Data record
├── export/
│   ├── DataExporter.java             Interface — swap export targets here
│   └── csv/CsvDataExporter.java      CSV implementation
└── pipeline/
    └── ReceiptProcessingPipeline.java  Orchestrates the full flow
```

## Extending

- **New OCR engine** — implement `OcrEngine`, register in `OcrEngineFactory`, set `ocr.engine=<key>`.
- **New email source** — implement `EmailService`, wire in `ReceiptProcessingPipeline.create()`.
- **New export target** — implement `DataExporter`, wire in `ReceiptProcessingPipeline.create()`.
