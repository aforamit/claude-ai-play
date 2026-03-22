package com.accounting.qbo.service;

import java.util.List;

/**
 * Service for generating QBO deposit JSON files from a CSV input.
 *
 * <p>Reads the deposits CSV and writes one JSON file per row into the data/test/ output folder.
 */
public interface CsvDepositService {

    /**
     * Reads the deposits CSV at the given path and generates one deposit JSON file
     * per row in {@code outputDir}.
     *
     * @param csvPath      absolute or relative path to the deposits CSV
     * @param invoicesJson path to the invoices JSON used to populate Line items
     * @param outputDir    directory where generated JSON files will be written
     * @return list of generated file names (not full paths)
     */
    List<String> generateDepositJsonFiles(String csvPath, String invoicesJson, String outputDir);
}
