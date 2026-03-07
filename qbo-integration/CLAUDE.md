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

## Windows 10 Deployment Notes

- JDK 21: https://adoptium.net/temurin/releases/?version=21
- Maven 3.9+: https://maven.apache.org/download.cgi
- Run: `run.cmd` in project root
- Credentials: set `QBO_CLIENT_ID` and `QBO_CLIENT_SECRET` as environment variables

---

## File Structure Summary

```
src/main/java/com/accounting/qbo/
├── QboApplication.java           ← Spring Boot entry point
├── auth/
│   ├── OAuthToken.java           ← Record: token value object
│   ├── TokenStore.java           ← Interface: token persistence
│   ├── InMemoryTokenStore.java   ← Default: in-memory (replace for prod)
│   └── QboOAuthService.java      ← OAuth 2.0 flow management
├── client/
│   └── QboApiClient.java         ← Single HTTP gateway to QBO API
├── config/
│   ├── QboProperties.java        ← @ConfigurationProperties: qbo.*
│   └── AppConfig.java            ← Spring beans: RestClient, ObjectMapper
├── controller/
│   ├── AuthController.java       ← /api/auth/*
│   ├── InvoiceController.java    ← /api/{realmId}/invoices/*
│   ├── DepositController.java    ← /api/{realmId}/deposits/*
│   └── AccountController.java   ← /api/{realmId}/accounts/*
├── dto/
│   └── ApiResponse.java          ← Generic API response wrapper record
├── exception/
│   ├── QboException.java         ← Runtime exception for QBO errors
│   └── GlobalExceptionHandler.java ← @RestControllerAdvice
├── model/
│   ├── EntityRef.java            ← Reusable QBO reference {id, name}
│   ├── Invoice.java / InvoiceLine.java
│   ├── Deposit.java / DepositLine.java
│   └── Account.java
└── service/
    ├── IQboEntityService.java    ← Generic root interface
    ├── InvoiceService.java       ← Invoice-specific interface
    ├── DepositService.java       ← Deposit-specific interface
    ├── AccountService.java       ← Account-specific interface
    └── impl/
        ├── InvoiceServiceImpl.java
        ├── DepositServiceImpl.java
        └── AccountServiceImpl.java
```
