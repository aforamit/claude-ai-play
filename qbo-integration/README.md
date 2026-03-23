# QuickBooks Online Integration Service

A modular, extensible Java 21 / Spring Boot 3 service for integrating with the QuickBooks Online (QBO) REST API. Built for accounting firms managing small business clients.

---

## High-Level Design

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Client (Browser / App)                        │
└─────────────────────────┬───────────────────────────────────────────┘
                          │ HTTP REST
┌─────────────────────────▼───────────────────────────────────────────┐
│                    Spring Boot REST API (Port 8080)                  │
│                                                                      │
│  ┌──────────────┐  ┌──────────────────┐  ┌───────────────────────┐  │
│  │AuthController│  │InvoiceController │  │   AccountController   │  │
│  │/api/auth/*   │  │/api/{id}/invoices│  │  /api/{id}/accounts   │  │
│  └──────┬───────┘  └────────┬─────────┘  └───────────┬───────────┘  │
│         │                  │                         │              │
│  ┌──────▼───────┐  ┌────────▼─────────────────────────▼──────────┐  │
│  │QboOAuthService│  │      Service Layer (Business Logic)          │  │
│  │ - Auth URL   │  │  InvoiceService / DepositService /            │  │
│  │ - Token swap │  │  AccountService / CsvDepositService /         │  │
│  │ - Refresh    │  │  ReconciliationService (interfaces + impls)   │  │
│  └──────┬───────┘  └──────────┬───────────────────────────────────┘  │
│         │                    │                                       │
│  ┌──────▼───────┐   ┌─────────▼──────────┐                          │
│  │  TokenStore  │   │   QboApiClient      │  ← live QBO calls only  │
│  │(InMemory/DB) │   │   - Bearer token    │                          │
│  └──────────────┘   │   - Query builder   │                          │
│                     │   - Error handling  │                          │
│                     └─────────┬───────────┘                          │
└───────────────────────────────┼─────────────────────────────────────┘
                                │ HTTPS
                 ┌──────────────▼──────────────┐
                 │   QuickBooks Online API v3   │
                 │  sandbox / production        │
                 │  Invoices / Deposits /       │
                 │  Accounts / Chart of Accts   │
                 └─────────────────────────────┘

  ┌──────────────────────────────────────────────────────────────────┐
  │                Offline / Local-File Features                      │
  │                                                                   │
  │  CsvDepositController  ──►  CsvDepositService                    │
  │  /api/csv/deposits/generate  reads deposits.csv                  │
  │                              + prod_invoices_*.json              │
  │                              writes one JSON per row → data/test/ │
  │                                                                   │
  │  ReconciliationController  ──►  ReconciliationService            │
  │  /api/reconcile              matches Invoices ↔ Deposits          │
  │                              via ACCOUNTABLE CASH lines           │
  │                              returns JSON + writes CSV audit log  │
  └──────────────────────────────────────────────────────────────────┘
```

---

## Architecture

### Layer Structure

| Layer | Package | Responsibility |
|-------|---------|---------------|
| **Controller** | `controller/` | REST endpoints, request/response mapping |
| **Service** | `service/` | Business logic, QBO query building, local-file processing |
| **Client** | `client/` | HTTP gateway to live QBO API (single responsibility) |
| **Auth** | `auth/` | OAuth 2.0 token lifecycle management |
| **Model** | `model/` | Domain objects mapped to QBO JSON |
| **DTO** | `dto/` | API response wrappers, reconciliation result type |
| **Config** | `config/` | Spring beans, configuration properties |
| **Exception** | `exception/` | Centralized error handling |

### Key Design Principles

- **Interface-first services** — Each entity has an interface. Swap implementations without touching controllers.
- **Single HTTP gateway** — `QboApiClient` is the only class that calls QBO. All live-API services depend on it.
- **Auto-refresh tokens** — `QboOAuthService.getValidToken()` transparently refreshes expired access tokens.
- **Multi-company** — All live-API endpoints accept `{realmId}` (QBO company ID) as a path parameter.
- **Configuration by environment variables** — No credentials in code or config files.
- **Offline pipeline** — CSV deposit generation and reconciliation work entirely from local JSON files; no live QBO connection required.

---

## Project Structure

```
qbo-integration/
├── pom.xml                              ← Maven build (Java 21, Spring Boot 3.2)
├── run.cmd                              ← Windows launch script
├── CLAUDE.md                            ← AI assistant context + extension guide
├── README.md                            ← This file
├── data/
│   ├── receipts/
│   │   └── deposits.csv                 ← Source CSV for CsvDepositService
│   ├── prod/
│   │   ├── prod_invoices_*.json         ← Invoice exports from QBO
│   │   ├── prod_deposits_*.json         ← Deposit exports from QBO
│   │   ├── bank_accounts.json           ← QBO bank accounts (Id + masked name)
│   │   └── classrefs.json               ← QBO class refs (store number + name)
│   └── test/                            ← Output for generated deposit JSONs
└── src/
    ├── main/
    │   ├── java/com/accounting/qbo/
    │   │   ├── QboApplication.java
    │   │   ├── auth/
    │   │   │   ├── OAuthToken.java          ← Record: token value object
    │   │   │   ├── TokenStore.java          ← Interface: pluggable token store
    │   │   │   ├── InMemoryTokenStore.java  ← Default (replace for production)
    │   │   │   └── QboOAuthService.java     ← OAuth 2.0 flow
    │   │   ├── client/
    │   │   │   └── QboApiClient.java        ← QBO HTTP gateway
    │   │   ├── config/
    │   │   │   ├── QboProperties.java       ← qbo.* configuration
    │   │   │   └── AppConfig.java           ← RestClient, ObjectMapper beans
    │   │   ├── controller/
    │   │   │   ├── HomeController.java      ← GET / — service info
    │   │   │   ├── AuthController.java      ← /api/auth/*
    │   │   │   ├── InvoiceController.java   ← /api/{realmId}/invoices/*
    │   │   │   ├── DepositController.java   ← /api/{realmId}/deposits/*
    │   │   │   ├── AccountController.java   ← /api/{realmId}/accounts/*
    │   │   │   ├── CsvDepositController.java    ← /api/csv/deposits/generate
    │   │   │   └── ReconciliationController.java ← /api/reconcile
    │   │   ├── dto/
    │   │   │   ├── ApiResponse.java         ← Generic response wrapper
    │   │   │   └── MatchedTransaction.java  ← Invoice–Deposit match result
    │   │   ├── exception/
    │   │   │   ├── QboException.java
    │   │   │   └── GlobalExceptionHandler.java
    │   │   ├── model/
    │   │   │   ├── EntityRef.java           ← Reusable {id, name} reference
    │   │   │   ├── Invoice.java + InvoiceLine.java
    │   │   │   ├── Deposit.java + DepositLine.java
    │   │   │   └── Account.java
    │   │   └── service/
    │   │       ├── IQboEntityService.java   ← Generic root interface
    │   │       ├── InvoiceService.java
    │   │       ├── DepositService.java
    │   │       ├── AccountService.java
    │   │       ├── CsvDepositService.java   ← CSV→JSON pipeline interface
    │   │       ├── ReconciliationService.java ← Reconciliation interface
    │   │       └── impl/
    │   │           ├── InvoiceServiceImpl.java
    │   │           ├── DepositServiceImpl.java
    │   │           ├── AccountServiceImpl.java
    │   │           ├── CsvDepositServiceImpl.java
    │   │           └── ReconciliationServiceImpl.java
    │   └── resources/
    │       └── application.yml
    └── test/
        ├── java/com/accounting/qbo/     ← 164 tests, all passing
        │   ├── QboApplicationTests.java
        │   ├── auth/
        │   │   ├── OAuthTokenTest.java
        │   │   └── QboOAuthServiceTest.java
        │   ├── client/
        │   │   └── QboApiClientTest.java
        │   ├── config/
        │   │   └── QboPropertiesTest.java
        │   ├── controller/
        │   │   ├── CsvDepositControllerIT.java      ← @SpringBootTest
        │   │   └── ReconciliationControllerIT.java  ← @SpringBootTest
        │   ├── dto/
        │   │   └── ApiResponseTest.java
        │   ├── exception/
        │   │   └── GlobalExceptionHandlerTest.java
        │   ├── model/
        │   │   └── InvoiceTest.java
        │   └── service/impl/
        │       ├── InvoiceServiceImplTest.java
        │       ├── DepositServiceImplTest.java
        │       ├── AccountServiceImplTest.java
        │       ├── CsvDepositServiceImplTest.java
        │       └── ReconciliationServiceImplTest.java
        └── resources/fixtures/          ← Test data (PineCrest Retail stores)
            ├── bank-accounts.json
            ├── classrefs.json
            ├── invoices-recon.json
            ├── deposits-recon.json
            └── invoices-csv-deposit.json
```

---

## Prerequisites

| Tool | Version | Download |
|------|---------|----------|
| JDK | 21 (Temurin recommended) | https://adoptium.net/temurin/releases/?version=21 |
| Maven | 3.9+ | https://maven.apache.org/download.cgi |
| QBO Developer Account | — | https://developer.intuit.com |

### Install JDK 21 on Windows 10

1. Download **Eclipse Temurin JDK 21** MSI installer from https://adoptium.net
2. Run installer — it sets `JAVA_HOME` and `PATH` automatically
3. Verify: open Command Prompt → `java -version`

### Install Maven on Windows 10

1. Download **apache-maven-3.9.x-bin.zip** from https://maven.apache.org/download.cgi
2. Extract to e.g. `C:\Workshop\Development\apache-maven-3.9.12`
3. Add `...\bin` to system `PATH`
4. Verify: `mvn -version`

---

## QuickBooks Online Developer Setup

### Step 1: Create Developer Account
1. Go to https://developer.intuit.com
2. Sign in or create an account

### Step 2: Create an App
1. Click **Dashboard** → **Create an app**
2. Select **QuickBooks Online and Payments**
3. Enter app name: e.g., `Accounting Firm Integration`
4. Select **com.intuit.quickbooks.accounting** scope

### Step 3: Get Credentials
1. Go to your app → **Keys & credentials** → **Sandbox**
2. Copy **Client ID** and **Client Secret**

### Step 4: Add Redirect URI
1. Go to **Redirect URIs**
2. Add: `http://localhost:8080/api/auth/callback`

### Step 5: Get a Sandbox Company
1. Go to https://developer.intuit.com/app/developer/playground
2. Connect to a sandbox company — note the **Realm ID** (company ID)

---

## Configuration

Set these environment variables **before** starting the application:

**Windows Command Prompt:**
```cmd
set QBO_CLIENT_ID=your-client-id-from-intuit
set QBO_CLIENT_SECRET=your-client-secret-from-intuit
set QBO_REDIRECT_URI=http://localhost:8080/api/auth/callback
set QBO_SANDBOX=true
```

**Windows PowerShell:**
```powershell
$env:QBO_CLIENT_ID = "your-client-id-from-intuit"
$env:QBO_CLIENT_SECRET = "your-client-secret-from-intuit"
$env:QBO_REDIRECT_URI = "http://localhost:8080/api/auth/callback"
$env:QBO_SANDBOX = "true"
```

**Persistent (System Environment Variables):**
1. Right-click **This PC** → **Properties**
2. Click **Advanced system settings** → **Environment Variables**
3. Add the above variables under **User variables**

---

## Running the Application

### Option 1: Using run.cmd (recommended for Windows)
```cmd
cd c:\Workshop\Workspace\claude-ai-play\qbo-integration
run.cmd
```

### Option 2: Using Maven directly
```cmd
cd c:\Workshop\Workspace\claude-ai-play\qbo-integration
mvn spring-boot:run
```

### Option 3: Build JAR and run
```cmd
mvn clean package -DskipTests
java -jar target/qbo-integration-1.0.0-SNAPSHOT.jar
```

---

## Running Tests

```cmd
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot
mvn test
```

All 164 tests should pass:

| Category | Test Classes | Tests |
|----------|-------------|-------|
| Auth | `OAuthTokenTest`, `QboOAuthServiceTest` | 28 |
| Client | `QboApiClientTest` | 14 |
| Config | `QboPropertiesTest` | 11 |
| DTO | `ApiResponseTest` | 6 |
| Exception | `GlobalExceptionHandlerTest` | 12 |
| Model | `InvoiceTest` | 9 |
| Services | `InvoiceServiceImplTest`, `DepositServiceImplTest`, `AccountServiceImplTest`, `CsvDepositServiceImplTest`, `ReconciliationServiceImplTest` | 68 |
| Integration | `CsvDepositControllerIT`, `ReconciliationControllerIT` | 15 |
| Smoke | `QboApplicationTests` | 1 |
| **Total** | | **164** |

---

## Authorization Flow (Live QBO)

Once the app is running, authorize it with QuickBooks:

1. Open browser → go to `http://localhost:8080/api/auth/connect`
2. You'll be redirected to Intuit's login page
3. Log in and authorize the app
4. Intuit redirects back with a token — you'll see a success response
5. The app now stores your tokens and auto-refreshes them

> **Note:** The `realmId` (company ID) is captured automatically during the callback and used in all subsequent API calls.

---

## API Endpoints

### Home
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/` | Service info, links to key endpoints |

### Authorization
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/auth/connect` | Start OAuth flow (browser redirect) |
| GET | `/api/auth/callback` | OAuth callback (automatic) |
| GET | `/api/auth/status/{realmId}` | Check token status |
| POST | `/api/auth/refresh/{realmId}` | Force token refresh |
| DELETE | `/api/auth/disconnect/{realmId}` | Revoke access |

### Invoices — `/api/{realmId}/invoices`
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/` | All invoices (up to 1000) |
| GET | `/{id}` | Invoice by ID |
| GET | `/unpaid` | Unpaid invoices (Balance > 0) |
| GET | `/overdue` | Overdue invoices (Balance > 0, past DueDate) |
| GET | `/by-customer/{customerId}` | Invoices for a customer |
| GET | `/by-date?from=2024-01-01&to=2024-12-31` | By transaction date range |
| GET | `/query?where=Balance > '500'` | Custom QBO WHERE clause |

### Deposits — `/api/{realmId}/deposits`
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/` | All deposits (up to 1000) |
| GET | `/{id}` | Deposit by ID |
| GET | `/by-date?from=2024-01-01&to=2024-12-31` | By date range |
| GET | `/by-account/{accountId}` | Deposits to a bank account |
| GET | `/query?where=TotalAmt > '1000'` | Custom QBO WHERE clause |

### Accounts (Chart of Accounts) — `/api/{realmId}/accounts`
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/` | All accounts |
| GET | `/{id}` | Account by ID |
| GET | `/active` | Active accounts only |
| GET | `/balance-sheet` | Asset + Liability + Equity accounts |
| GET | `/income-statement` | Revenue + Expense accounts (P&L) |
| GET | `/by-type?type=Bank` | By AccountType (Bank, Income, Expense, etc.) |
| GET | `/by-classification?classification=Asset` | By classification |
| GET | `/query?where=AccountType = 'Bank'` | Custom QBO WHERE clause |

### CSV Deposit Generation — `/api/csv/deposits`
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/generate` | Read deposits.csv → write one QBO deposit JSON per row |

**Query parameters (both optional):**
- `csvPath` — path to source CSV (default: `data/receipts/deposits.csv`)
- `outputDir` — output directory for generated JSON files (default: `data/test`)

**Example:**
```bash
curl "http://localhost:8080/api/csv/deposits/generate"
curl "http://localhost:8080/api/csv/deposits/generate?csvPath=data/receipts/march.csv&outputDir=data/test"
```

The service reads invoice data from all `prod_invoices_*.json` files in `data/prod/` automatically. Each output file represents a fully constructed QBO Deposit object ready to be posted.

### Reconciliation — `/api/reconcile`
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/reconcile` | Match invoices ↔ deposits, return matches + write CSV |

**Query parameters (all required):**
- `invoiceFile` — path to invoices JSON (e.g., `data/prod/prod_invoices_260201-260208.json`)
- `depositFile` — path to deposits JSON (e.g., `data/prod/prod_deposits_260201-260208.json`)
- `csvOutput` — path where the reconciliation CSV will be written

**Example:**
```bash
curl "http://localhost:8080/api/reconcile?invoiceFile=data/prod/prod_invoices_260201-260208.json&depositFile=data/prod/prod_deposits_260201-260208.json&csvOutput=data/reconciliation_output.csv"
```

**Matching logic:** A Deposit line with `Description == "ACCOUNTABLE CASH"` has a `LinkedTxn.TxnId` pointing to an Invoice by its `Id`. Matched pairs are returned as JSON and also written to the CSV file.

**Response fields per matched pair:**

| Field | Description |
|-------|-------------|
| `invoiceId` | QBO Invoice ID |
| `invoiceDocNumber` | Invoice document number |
| `invoiceTxnDate` | Invoice transaction date |
| `invoiceDueDate` | Invoice due date |
| `invoiceCustomer` | Customer name on invoice |
| `invoiceLocation` | Class/location on invoice |
| `invoiceAccountableCash` | ACCOUNTABLE CASH line amount on invoice |
| `depositId` | QBO Deposit ID |
| `depositTxnDate` | Deposit transaction date |
| `depositTotalAmount` | Total deposit amount |
| `depositAccount` | Bank account name |
| `accountableCashAmount` | ACCOUNTABLE CASH line amount in deposit |

### Health Check
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/actuator/health` | Application health |
| GET | `/actuator/info` | App information |

---

## Example API Calls

Replace `{realmId}` with your QBO company ID (captured during OAuth).

```bash
# Check auth status
curl http://localhost:8080/api/auth/status/4620816365331369327

# Get all unpaid invoices
curl http://localhost:8080/api/4620816365331369327/invoices/unpaid

# Get invoices for a date range
curl "http://localhost:8080/api/4620816365331369327/invoices/by-date?from=2024-01-01&to=2024-12-31"

# Get all bank accounts
curl "http://localhost:8080/api/4620816365331369327/accounts/by-type?type=Bank"

# Get balance sheet accounts
curl http://localhost:8080/api/4620816365331369327/accounts/balance-sheet

# Get deposits for Q1 2024
curl "http://localhost:8080/api/4620816365331369327/deposits/by-date?from=2024-01-01&to=2024-03-31"

# Custom query — invoices over $5000
curl "http://localhost:8080/api/4620816365331369327/invoices/query?where=TotalAmt > '5000'"

# Generate deposit JSON files from CSV
curl "http://localhost:8080/api/csv/deposits/generate"

# Reconcile a specific period
curl "http://localhost:8080/api/reconcile?invoiceFile=data/prod/prod_invoices_260201-260208.json&depositFile=data/prod/prod_deposits_260201-260208.json&csvOutput=data/reconciliation_output.csv"
```

---

## Extending the Application

### Add a New QBO Entity (e.g., Bill, Payment, Customer)

1. **Model** — `src/main/java/com/accounting/qbo/model/Bill.java`
   - Annotate fields with `@JsonProperty("FieldName")` matching QBO API names
   - Use `@JsonIgnoreProperties(ignoreUnknown = true)`

2. **Service Interface** — `service/BillService.java`
   - Extend `IQboEntityService<Bill>`
   - Add entity-specific methods

3. **Implementation** — `service/impl/BillServiceImpl.java`
   - Inject `QboApiClient`
   - Build QBO SQL queries in each method

4. **Controller** — `controller/BillController.java`
   - `@RequestMapping("/api/{realmId}/bills")`
   - Delegate to `BillService`

### Replace In-Memory Token Store with Database Storage

```java
@Component
@Primary  // overrides InMemoryTokenStore
public class JdbcTokenStore implements TokenStore {
    // implement save(), get(), delete() using JDBC/JPA
}
```

### Add Response Caching

```java
// In pom.xml: add spring-boot-starter-cache
// In service:
@Cacheable(value = "accounts", key = "#realmId")
public List<Account> findAll(String realmId) { ... }
```

---

## Response Format

All endpoints return a consistent JSON structure:

**Success (list):**
```json
{
  "success": true,
  "count": 5,
  "data": [ ... ],
  "timestamp": "2024-01-15T10:00:00Z"
}
```

**Success (single):**
```json
{
  "success": true,
  "count": 1,
  "data": { ... },
  "timestamp": "2024-01-15T10:00:00Z"
}
```

**Error:**
```json
{
  "success": false,
  "message": "Not authorized for realmId: 123. Visit /api/auth/connect to authorize.",
  "timestamp": "2024-01-15T10:00:00Z"
}
```

---

## Security Notes

- Never commit `QBO_CLIENT_ID` or `QBO_CLIENT_SECRET` to source control
- Tokens are stored in-memory by default — lost on restart
- For production: implement `TokenStore` backed by encrypted database storage
- For production: use HTTPS (configure SSL in `application.yml` or use a reverse proxy like nginx)
- For production: restrict actuator endpoints and add authentication

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| `java: command not found` | Install JDK 21, add `%JAVA_HOME%\bin` to `PATH` |
| `mvn: command not found` | Install Maven, add `%MAVEN_HOME%\bin` to `PATH` |
| `401 Unauthorized` | Token expired or not set — visit `/api/auth/connect` |
| `QBO API fault [AUTHENTICATION]` | Client ID/Secret incorrect — check Intuit developer portal |
| `redirect_uri_mismatch` | Add `http://localhost:8080/api/auth/callback` to Intuit app's redirect URIs |
| Tokens lost after restart | Implement database-backed `TokenStore` |
| `502 Bad Gateway` in response | QBO API error — check logs for details |
| Tests fail with JVM version error | Ensure `JAVA_HOME` points to JDK 21 when running `mvn test` |
| CSV generate returns empty list | Verify `data/prod/prod_invoices_*.json` and `data/prod/classrefs.json` exist |
| Reconcile returns 0 matches | Check deposit lines have `Description == "ACCOUNTABLE CASH"` with valid `LinkedTxn.TxnId` |
