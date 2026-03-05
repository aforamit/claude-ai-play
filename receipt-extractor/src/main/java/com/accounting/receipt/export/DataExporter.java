package com.accounting.receipt.export;

import com.accounting.receipt.model.DepositRecord;

import java.util.List;

/**
 * Strategy interface for exporting extracted deposit records.
 *
 * To add a new export target (Excel, database, REST API, etc.):
 *   1. Create a class implementing this interface.
 *   2. Wire it up in {@link com.accounting.receipt.pipeline.ReceiptProcessingPipeline}.
 */
public interface DataExporter {

    /**
     * Persists the deposit records to the configured destination.
     *
     * @param records records to export; will not be null but may be empty
     * @return number of records actually written
     */
    int export(List<DepositRecord> records) throws Exception;

    /** Human-readable description of the output destination (used in logs). */
    String getDestinationDescription();
}
