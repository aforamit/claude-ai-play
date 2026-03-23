# CLAUDE.md — QBO Integration Project Context

## Project Origin Prompt

> You are a senior software engineer helping with solution design and implementation of the automation platform for small businesses.
>
> I'm an Accounting firm helping small businesses with their accounting activities. As part of the automation, I need to integrate QuickBook Online APIs to query Invoice, Deposit and Account details.
>
> Prepare a high level design of how the above application would look like in Java.
> Create a new Java 21 project to implement this requirement. Make the project modular and extendable. The design should be as such that I should be able to Query API to integrate and extract the information, which I should be able to extend for further enhancements.
>
> Create CLAUDE.md file that has the complete prompt available for context and README.md with all the necessary details about the projects including description, architecture, design, how to execute command, project structure etc.
>
> I should be able to deploy the application easily and make it working on my Windows 10 PC.

---

## Project: QuickBooks Online Integration Service

### Stack
- **Java 21** (records, pattern matching, text blocks)
- **Spring Boot 3.2** (web, actuator, RestClient)
- **Jackson** (JSON, JSR-310 dates)
- **Maven** build
- **JUnit 5 + Mockito 5** (unit tests, integration tests — 164 tests, all passing)

### Key Design Decisions

1. **No Intuit Java SDK** — Uses Spring RestClient directly for HTTP calls.
   This avoids heavy SDK dependencies and gives full control of requests/responses.

2. **OAuth 2.0 Authorization Code Flow** — Tokens managed by `QboOAuthService`.
   Auto-refresh on expiry. `TokenStore` interface for swappable storage.

3. **Generic service interface** — `IQboEntityService<T>` is the root extension point.
   Add new QBO entities (Bill, Payment, Customer) by implementing this interface.

4. **`QboApiClient` is the single HTTP gateway** — All services use it.
   Handles token injection, URI building, QBO error parsing.

5. **Multi-company support** — Every API call accepts a `realmId` path parameter.

6. **CSV-to-JSON deposit pipeline** — `CsvDepositService` reads a bank deposit CSV,
   enriches each row with invoice line data from local JSON files, and writes one
   QBO deposit JSON file per row to an output directory.

7. **Invoice–Deposit reconciliation** — `ReconciliationService` matches Invoices with
   Deposits via `ACCOUNTABLE CASH` deposit lines (where `LinkedTxn.TxnId == Invoice.Id`),
   returns matched pairs as JSON, and writes a CSV audit trail.

---

## Extension Patterns

### Add a new QBO entity (e.g., Bill)

1. Create `model/Bill.java` — map QBO fields with `@JsonProperty`
2. Create `service/BillService.java` — extend `IQboEntityService<Bill>`
3. Create `service/impl/BillServiceImpl.java` — inject `QboApiClient`, build queries
4. Create `controller/BillController.java` — `@RequestMapping("/api/{realmId}/bills")`

No other files need modification.

### Replace token storage with database persistence

1. Create a class implementing `TokenStore` (e.g., `JdbcTokenStore`)
2. Annotate with `@Component` and `@Primary`
3. Spring auto-wires the new implementation everywhere

### Add caching

Use `@Cacheable` from `spring-boot-starter-cache` on any service method.

---

## QBO API Reference

- **Base URL (Sandbox)**: `https://sandbox-quickbooks.api.intuit.com/v3/company/{realmId}/`
- **Base URL (Production)**: `https://quickbooks.api.intuit.com/v3/company/{realmId}/`
- **Query endpoint**: `/query?query=SELECT * FROM {Entity}&minorversion=65`
- **Read endpoint**: `/{entity}/{id}?minorversion=65`
- **Auth URL**: `https://appcenter.intuit.com/connect/oauth2`
- **Token URL**: `https://oauth.platform.intuit.com/oauth2/v1/tokens/bearer`
- **Developer Portal**: https://developer.intuit.com

### QBO SQL Query Syntax
```sql
SELECT * FROM Invoice
SELECT * FROM Invoice WHERE Balance > '0' ORDERBY DueDate ASC
SELECT * FROM Deposit WHERE TxnDate >= '2024-01-01' AND TxnDate <= '2024-12-31'
SELECT * FROM Account WHERE AccountType = 'Bank' AND Active = true
```

---

## Local Data File Conventions

The CSV deposit and reconciliation features use local JSON files — not live QBO API calls.
These files are expected under the `data/` folder in the project root:

```
data/
├── receipts/
│   └── deposits.csv              ← Source CSV for CsvDepositService
├── prod/
│   ├── prod_invoices_*.json      ← Invoice exports from QBO (one file per period)
│   ├── prod_deposits_*.json      ← Deposit exports from QBO
│   ├── bank_accounts.json        ← QBO bank account list (Id + Name)
│   └── classrefs.json            ← QBO class refs (store number + name)
└── test/                         ← Output dir for generated deposit JSON files
```

### CSV Deposit Format (deposits.csv)
```
"Store","Account","Deposit Date","Balance Date","Deposit Amt","Balance Amt"
"1174","*****1729","03-06-2026","03-01-2026","454.88","454.88"
```
- `Store` — matches the store number in `classrefs.json`
- `Account` — matched against the masked account number in `bank_accounts.json`
- `Balance Date` — used to look up the matching invoice by `DocNumber` (format `YYMMDD-StoreId`)

---

## Windows 10 Deployment Notes

- JDK 21: https://adoptium.net/temurin/releases/?version=21
- Maven 3.9+: https://maven.apache.org/download.cgi
- Run: `run.cmd` in project root
- Credentials: set `QBO_CLIENT_ID` and `QBO_CLIENT_SECRET` as environment variables
- Run tests: `JAVA_HOME="..." mvn test` (ensure JDK 21 is used for the forked test JVM)

---

## File Structure Summary

```
src/main/java/com/accounting/qbo/
├── QboApplication.java
├── auth/
│   ├── OAuthToken.java             ← Record: token value object
│   ├── TokenStore.java             ← Interface: token persistence
│   ├── InMemoryTokenStore.java     ← Default: in-memory (replace for prod)
│   └── QboOAuthService.java        ← OAuth 2.0 flow management
├── client/
│   └── QboApiClient.java           ← Single HTTP gateway to QBO API
├── config/
│   ├── QboProperties.java          ← @ConfigurationProperties: qbo.*
│   └── AppConfig.java              ← Spring beans: RestClient, ObjectMapper
├── controller/
│   ├── HomeController.java         ← GET / — service info
│   ├── AuthController.java         ← /api/auth/*
│   ├── InvoiceController.java      ← /api/{realmId}/invoices/*
│   ├── DepositController.java      ← /api/{realmId}/deposits/*
│   ├── AccountController.java      ← /api/{realmId}/accounts/*
│   ├── CsvDepositController.java   ← /api/csv/deposits/generate
│   └── ReconciliationController.java ← /api/reconcile
├── dto/
│   ├── ApiResponse.java            ← Generic API response wrapper record
│   └── MatchedTransaction.java     ← Invoice–Deposit match result
├── exception/
│   ├── QboException.java
│   └── GlobalExceptionHandler.java ← @RestControllerAdvice
├── model/
│   ├── EntityRef.java              ← Reusable QBO reference {id, name}
│   ├── Invoice.java / InvoiceLine.java
│   ├── Deposit.java / DepositLine.java
│   └── Account.java
└── service/
    ├── IQboEntityService.java      ← Generic root interface
    ├── InvoiceService.java
    ├── DepositService.java
    ├── AccountService.java
    ├── CsvDepositService.java      ← CSV→JSON deposit file generation
    ├── ReconciliationService.java  ← Invoice–Deposit matching
    └── impl/
        ├── InvoiceServiceImpl.java
        ├── DepositServiceImpl.java
        ├── AccountServiceImpl.java
        ├── CsvDepositServiceImpl.java
        └── ReconciliationServiceImpl.java

src/test/java/com/accounting/qbo/       ← 164 tests, all passing
├── QboApplicationTests.java
├── auth/
│   ├── OAuthTokenTest.java             ← 10 tests
│   └── QboOAuthServiceTest.java        ← 18 tests
├── client/
│   └── QboApiClientTest.java           ← 14 tests
├── config/
│   └── QboPropertiesTest.java          ← 11 tests
├── controller/
│   ├── CsvDepositControllerIT.java     ← 8 integration tests (@SpringBootTest)
│   └── ReconciliationControllerIT.java ← 7 integration tests (@SpringBootTest)
├── dto/
│   └── ApiResponseTest.java            ← 6 tests
├── exception/
│   └── GlobalExceptionHandlerTest.java ← 12 tests
├── model/
│   └── InvoiceTest.java                ← 9 tests
└── service/impl/
    ├── InvoiceServiceImplTest.java     ← 9 tests
    ├── DepositServiceImplTest.java     ← 7 tests
    ├── AccountServiceImplTest.java     ← 11 tests
    ├── CsvDepositServiceImplTest.java  ← 22 tests
    └── ReconciliationServiceImplTest.java ← 19 tests

src/test/resources/fixtures/           ← Test data fixtures
├── bank-accounts.json                  ← PineCrest Retail stores (3 accounts)
├── classrefs.json                      ← PineCrest Retail class refs (3 stores)
├── invoices-recon.json                 ← 3 invoices for reconciliation tests
├── deposits-recon.json                 ← 4 deposits for reconciliation tests
└── invoices-csv-deposit.json           ← Minimal invoices for CsvDeposit tests
```
