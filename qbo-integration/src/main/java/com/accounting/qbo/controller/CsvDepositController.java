package com.accounting.qbo.controller;

import com.accounting.qbo.dto.ApiResponse;
import com.accounting.qbo.service.CsvDepositService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API for CSV-to-JSON deposit file generation.
 *
 * <p>Available endpoints:
 * <ul>
 *   <li>GET /api/csv/deposits/generate  – Read deposits.csv and write JSON files to data/test/</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/csv/deposits")
public class CsvDepositController {

    private static final String DEFAULT_CSV    = "data/receipts/deposits.csv";
    private static final String DEFAULT_OUTPUT = "data/test";

    private final CsvDepositService csvDepositService;

    public CsvDepositController(CsvDepositService csvDepositService) {
        this.csvDepositService = csvDepositService;
    }

    /**
     * Reads the deposits CSV and generates one QBO deposit JSON file per row in data/test/.
     * Invoice line data is loaded automatically from all prod_invoices_*.json files in data/prod/.
     *
     * @param csvPath   path to the source CSV (default: data/receipts/deposits.csv)
     * @param outputDir output directory for generated JSON files (default: data/test)
     * @return list of generated file names
     */
    @GetMapping("/generate")
    public ResponseEntity<ApiResponse<List<String>>> generate(
            @RequestParam(defaultValue = DEFAULT_CSV)    String csvPath,
            @RequestParam(defaultValue = DEFAULT_OUTPUT) String outputDir) {

        List<String> files = csvDepositService.generateDepositJsonFiles(csvPath, outputDir);
        return ResponseEntity.ok(ApiResponse.ofList(files));
    }
}
