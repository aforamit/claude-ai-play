package com.accounting.qbo.controller;

import com.accounting.qbo.dto.ApiResponse;
import com.accounting.qbo.model.Deposit;
import com.accounting.qbo.service.DepositService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * REST API for QuickBooks Online Deposit operations.
 *
 * <p>Available endpoints:
 * <ul>
 *   <li>GET /api/{realmId}/deposits                        – All deposits</li>
 *   <li>GET /api/{realmId}/deposits/{id}                   – Deposit by ID</li>
 *   <li>GET /api/{realmId}/deposits/by-date?from=&to=      – By date range</li>
 *   <li>GET /api/{realmId}/deposits/by-account/{accountId} – By bank account</li>
 *   <li>GET /api/{realmId}/deposits/query?where=           – Custom QBO query</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/{realmId}/deposits")
public class DepositController {

    private final DepositService depositService;

    public DepositController(DepositService depositService) {
        this.depositService = depositService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Deposit>>> getAll(@PathVariable String realmId) {
        List<Deposit> deposits = depositService.findAll(realmId);
        return ResponseEntity.ok(ApiResponse.ofList(deposits));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Deposit>> getById(
            @PathVariable String realmId,
            @PathVariable String id) {
        return depositService.findById(realmId, id)
                .map(deposit -> ResponseEntity.ok(ApiResponse.ofOne(deposit)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-date")
    public ResponseEntity<ApiResponse<List<Deposit>>> getByDateRange(
            @PathVariable String realmId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<Deposit> deposits = depositService.findByDateRange(realmId, from, to);
        return ResponseEntity.ok(ApiResponse.ofList(deposits));
    }

    @GetMapping("/by-account/{accountId}")
    public ResponseEntity<ApiResponse<List<Deposit>>> getByAccount(
            @PathVariable String realmId,
            @PathVariable String accountId) {
        List<Deposit> deposits = depositService.findByAccountId(realmId, accountId);
        return ResponseEntity.ok(ApiResponse.ofList(deposits));
    }

    @GetMapping("/query")
    public ResponseEntity<ApiResponse<List<Deposit>>> customQuery(
            @PathVariable String realmId,
            @RequestParam String where) {
        List<Deposit> deposits = depositService.query(realmId, where);
        return ResponseEntity.ok(ApiResponse.ofList(deposits));
    }
}
