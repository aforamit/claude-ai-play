package com.accounting.qbo.controller;

import com.accounting.qbo.dto.ApiResponse;
import com.accounting.qbo.model.Invoice;
import com.accounting.qbo.service.InvoiceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * REST API for QuickBooks Online Invoice operations.
 *
 * <p>All endpoints require a {@code realmId} path variable (QBO company ID).
 *
 * <p>Available endpoints:
 * <ul>
 *   <li>GET /api/{realmId}/invoices                            – All invoices</li>
 *   <li>GET /api/{realmId}/invoices/{id}                       – Invoice by ID</li>
 *   <li>GET /api/{realmId}/invoices/unpaid                     – Unpaid invoices</li>
 *   <li>GET /api/{realmId}/invoices/overdue                    – Overdue invoices</li>
 *   <li>GET /api/{realmId}/invoices/by-customer/{customerId}   – By customer</li>
 *   <li>GET /api/{realmId}/invoices/by-date?from=&to=          – By date range</li>
 *   <li>GET /api/{realmId}/invoices/query?where=               – Custom QBO query</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/{realmId}/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Invoice>>> getAll(@PathVariable String realmId) {
        List<Invoice> invoices = invoiceService.findAll(realmId);
        return ResponseEntity.ok(ApiResponse.ofList(invoices));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Invoice>> getById(
            @PathVariable String realmId,
            @PathVariable String id) {
        return invoiceService.findById(realmId, id)
                .map(invoice -> ResponseEntity.ok(ApiResponse.ofOne(invoice)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/unpaid")
    public ResponseEntity<ApiResponse<List<Invoice>>> getUnpaid(@PathVariable String realmId) {
        List<Invoice> invoices = invoiceService.findUnpaid(realmId);
        return ResponseEntity.ok(ApiResponse.ofList(invoices));
    }

    @GetMapping("/overdue")
    public ResponseEntity<ApiResponse<List<Invoice>>> getOverdue(@PathVariable String realmId) {
        List<Invoice> invoices = invoiceService.findOverdue(realmId);
        return ResponseEntity.ok(ApiResponse.ofList(invoices));
    }

    @GetMapping("/by-customer/{customerId}")
    public ResponseEntity<ApiResponse<List<Invoice>>> getByCustomer(
            @PathVariable String realmId,
            @PathVariable String customerId) {
        List<Invoice> invoices = invoiceService.findByCustomerId(realmId, customerId);
        return ResponseEntity.ok(ApiResponse.ofList(invoices));
    }

    @GetMapping("/by-date")
    public ResponseEntity<ApiResponse<List<Invoice>>> getByDateRange(
            @PathVariable String realmId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<Invoice> invoices = invoiceService.findByDateRange(realmId, from, to);
        return ResponseEntity.ok(ApiResponse.ofList(invoices));
    }

    @GetMapping("/query")
    public ResponseEntity<ApiResponse<List<Invoice>>> customQuery(
            @PathVariable String realmId,
            @RequestParam String where) {
        List<Invoice> invoices = invoiceService.query(realmId, where);
        return ResponseEntity.ok(ApiResponse.ofList(invoices));
    }
}
