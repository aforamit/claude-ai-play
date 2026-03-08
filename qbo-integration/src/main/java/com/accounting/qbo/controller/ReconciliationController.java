package com.accounting.qbo.controller;

import com.accounting.qbo.dto.ApiResponse;
import com.accounting.qbo.dto.MatchedTransaction;
import com.accounting.qbo.service.ReconciliationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for reconciling Invoices with Deposits.
 *
 * <p>Reads local JSON data files, matches Invoice.Id with Deposit line
 * LinkedTxn.TxnId where Description is "ACCOUNTABLE CASH", and returns
 * the matched pairs as JSON. Also writes a CSV file to disk.
 */
@RestController
@RequestMapping("/api/reconcile")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /**
     * GET /api/reconcile
     *
     * <p>Query params (all optional — default to files in data/ folder):
     * <ul>
     *   <li>{@code invoiceFile} — path to invoices JSON (default: data/prod_invoices_260201-260208.json)</li>
     *   <li>{@code depositFile} — path to deposits JSON (default: data/prod_deposits_260201-260208.json)</li>
     *   <li>{@code csvOutput}   — path for the CSV output (default: data/reconciliation_output.csv)</li>
     * </ul>
     */
    @GetMapping
    public ApiResponse<List<MatchedTransaction>> reconcile(
            @RequestParam String invoiceFile,
            @RequestParam String depositFile,
            @RequestParam String csvOutput) {

        List<MatchedTransaction> matches = reconciliationService.reconcile(invoiceFile, depositFile, csvOutput);
        return ApiResponse.ofList(matches);
    }
}
