package com.accounting.qbo.service;

import com.accounting.qbo.dto.MatchedTransaction;

import java.util.List;

/**
 * Matches Invoices with Deposits via "ACCOUNTABLE CASH" lines.
 *
 * <p>A Deposit line with {@code Description == "ACCOUNTABLE CASH"} contains a
 * {@code LinkedTxn.TxnId} that references an Invoice by its {@code Id}.
 * This service finds all such matches and optionally exports them to CSV.
 */
public interface ReconciliationService {

    /**
     * Loads Invoices and Deposits from the local JSON data files,
     * matches them, writes a CSV, and returns the matched list.
     *
     * @param invoiceFile  path to the invoices JSON file
     * @param depositFile  path to the deposits JSON file
     * @param csvOutputPath path where the CSV will be written
     * @return list of matched Invoice–Deposit pairs
     */
    List<MatchedTransaction> reconcile(String invoiceFile, String depositFile, String csvOutputPath);
}
