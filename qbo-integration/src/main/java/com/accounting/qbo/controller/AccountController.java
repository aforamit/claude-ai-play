package com.accounting.qbo.controller;

import com.accounting.qbo.dto.ApiResponse;
import com.accounting.qbo.model.Account;
import com.accounting.qbo.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for QuickBooks Online Account (Chart of Accounts) operations.
 *
 * <p>Available endpoints:
 * <ul>
 *   <li>GET /api/{realmId}/accounts                     – All accounts</li>
 *   <li>GET /api/{realmId}/accounts/{id}                – Account by ID</li>
 *   <li>GET /api/{realmId}/accounts/active              – Active accounts only</li>
 *   <li>GET /api/{realmId}/accounts/balance-sheet       – Balance sheet accounts</li>
 *   <li>GET /api/{realmId}/accounts/income-statement    – P&L accounts</li>
 *   <li>GET /api/{realmId}/accounts/by-type?type=       – By AccountType</li>
 *   <li>GET /api/{realmId}/accounts/by-classification?classification= – By classification</li>
 *   <li>GET /api/{realmId}/accounts/query?where=        – Custom QBO query</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/{realmId}/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Account>>> getAll(@PathVariable String realmId) {
        List<Account> accounts = accountService.findAll(realmId);
        return ResponseEntity.ok(ApiResponse.ok(accounts));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Account>> getById(
            @PathVariable String realmId,
            @PathVariable String id) {
        return accountService.findById(realmId, id)
                .map(account -> ResponseEntity.ok(ApiResponse.ok(account)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<Account>>> getActive(@PathVariable String realmId) {
        List<Account> accounts = accountService.findActive(realmId);
        return ResponseEntity.ok(ApiResponse.ok(accounts));
    }

    @GetMapping("/balance-sheet")
    public ResponseEntity<ApiResponse<List<Account>>> getBalanceSheet(@PathVariable String realmId) {
        List<Account> accounts = accountService.findBalanceSheetAccounts(realmId);
        return ResponseEntity.ok(ApiResponse.ok(accounts));
    }

    @GetMapping("/income-statement")
    public ResponseEntity<ApiResponse<List<Account>>> getIncomeStatement(@PathVariable String realmId) {
        List<Account> accounts = accountService.findIncomeStatementAccounts(realmId);
        return ResponseEntity.ok(ApiResponse.ok(accounts));
    }

    @GetMapping("/by-type")
    public ResponseEntity<ApiResponse<List<Account>>> getByType(
            @PathVariable String realmId,
            @RequestParam String type) {
        List<Account> accounts = accountService.findByType(realmId, type);
        return ResponseEntity.ok(ApiResponse.ok(accounts));
    }

    @GetMapping("/by-classification")
    public ResponseEntity<ApiResponse<List<Account>>> getByClassification(
            @PathVariable String realmId,
            @RequestParam String classification) {
        List<Account> accounts = accountService.findByClassification(realmId, classification);
        return ResponseEntity.ok(ApiResponse.ok(accounts));
    }

    @GetMapping("/query")
    public ResponseEntity<ApiResponse<List<Account>>> customQuery(
            @PathVariable String realmId,
            @RequestParam String where) {
        List<Account> accounts = accountService.query(realmId, where);
        return ResponseEntity.ok(ApiResponse.ok(accounts));
    }
}
