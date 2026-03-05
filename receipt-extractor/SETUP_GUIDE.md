# Cash Deposit Receipt Extractor — Setup Guide

## Prerequisites

### 1. Java 21 JDK
Download and install from: https://adoptium.net
Choose **Temurin 21 LTS** → Windows x64 installer.
The installer adds Java to your PATH automatically.

Verify: open **Command Prompt** and run:
```
java -version
```

### 2. Apache Maven 3.9+
Download from: https://maven.apache.org/download.cgi
Extract to e.g. `C:\Program Files\Maven\`, then add `bin\` to your PATH.

Verify:
```
mvn -version
```

---

## Step A — Google Cloud / Gmail Setup

### A1. Create a Google Cloud Project
1. Go to https://console.cloud.google.com
2. Click **New Project** → name it (e.g. `receipt-extractor`) → **Create**

### A2. Enable the Gmail API
1. In the project dashboard, go to **APIs & Services → Library**
2. Search for **Gmail API** → click it → **Enable**

### A3. Create OAuth Credentials
1. Go to **APIs & Services → Credentials**
2. Click **Create Credentials → OAuth client ID**
3. If prompted, configure the **OAuth consent screen** first:
   - User Type: **External**
   - App name: `Receipt Extractor`
   - Add your Gmail address as a test user
   - Save
4. Back in **Create OAuth client ID**:
   - Application type: **Desktop app**
   - Name: `Receipt Extractor Desktop`
   - Click **Create**
5. Click **Download JSON** — rename the downloaded file to `credentials.json`
6. Place `credentials.json` in the same folder as `run.bat`

### A4. First-Run Authentication
On the **first run** the application will:
1. Print a URL in the console
2. Open your default browser automatically
3. Ask you to sign in with your Gmail account
4. Ask you to grant permission to read your emails
5. Redirect back — the application resumes automatically

The token is saved to the `tokens/` folder.
**Subsequent runs are silent** (no browser needed).

---

## Step B — Anthropic API Key

1. Sign up / log in at: https://console.anthropic.com
2. Go to **API Keys** → **Create Key**
3. Copy the key (starts with `sk-ant-...`)
4. Open `application.properties` and replace:
   ```
   ocr.claude.api-key=YOUR_ANTHROPIC_API_KEY_HERE
   ```
   with your actual key.

Alternatively, set the environment variable `ANTHROPIC_API_KEY` before running.

---

## Step C — Build & Run

### Build
Double-click **`setup.bat`** (or run in Command Prompt):
```
setup.bat
```
This runs `mvn clean package` and produces `target/receipt-extractor-1.0.0.jar`.

### Run
Double-click **`run.bat`** (or):
```
run.bat
```

---

## Output

The application writes to **`deposits.csv`** in the same folder:

```
Store ID,Date (MM-DD-YYYY),Amount ($)
Store-101,01-15-2024,1250.75
Store-102,01-15-2024,870.00
Store-101,01-16-2024,995.50
```

Logs are written to the **`logs/`** folder.

---

## Configuration Reference (`application.properties`)

| Key | Default | Description |
|---|---|---|
| `ocr.engine` | `claude` | OCR engine: `claude`, `google-vision`, `tesseract` |
| `ocr.claude.api-key` | — | Anthropic API key |
| `ocr.claude.model` | `claude-opus-4-6` | Claude model to use |
| `gmail.credentials.path` | `credentials.json` | Path to OAuth credentials file |
| `gmail.tokens.path` | `tokens` | Directory where the OAuth token is cached |
| `email.max-fetch` | `10` | Max unread emails to check per run |
| `email.mark-as-read` | `true` | Mark processed emails as read |
| `output.csv.path` | `deposits.csv` | Output CSV file path |
| `output.csv.append` | `true` | Append to CSV or overwrite each run |

---

## Scheduling (Windows Task Scheduler)

To run automatically every day:

1. Open **Task Scheduler** → **Create Basic Task**
2. Name: `Receipt Extractor`
3. Trigger: **Daily** at your preferred time
4. Action: **Start a program**
   - Program: `java`
   - Arguments: `-jar "C:\path\to\receipt-extractor\target\receipt-extractor-1.0.0.jar"`
   - Start in: `C:\path\to\receipt-extractor\`
5. Finish

---

## Switching OCR Engine

| Engine | Config | Notes |
|---|---|---|
| Claude Vision | `ocr.engine=claude` | **Default.** Best accuracy. Requires Anthropic API key. |
| Google Vision | `ocr.engine=google-vision` | Good OCR. Requires Google Cloud credentials. Stub — needs implementation. |
| Tesseract | `ocr.engine=tesseract` | Offline, free. Lower accuracy. Stub — needs implementation. |

---

## Troubleshooting

**"credentials.json not found"**
→ Download from Google Cloud Console and place it next to `run.bat`.

**"Anthropic API key not configured"**
→ Set `ocr.claude.api-key` in `application.properties`.

**"No unread emails found"**
→ The app only processes **unread** emails with image attachments. Make sure the store manager emails are unread and contain receipt images (JPG, PNG, etc.).

**OAuth browser doesn't open**
→ Copy the URL from the console output and paste it manually into your browser.

**Records show "UNKNOWN" for Store ID**
→ The store ID may not be clearly visible in the receipt image. Ask store managers to ensure the store number is printed on receipts.
