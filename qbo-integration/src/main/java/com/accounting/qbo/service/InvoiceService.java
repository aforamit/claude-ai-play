package com.accounting.qbo.service;

import com.accounting.qbo.model.Invoice;

import java.time.LocalDate;
import java.util.List;

/**
 * Service contract for QBO Invoice operations.
 *
 * <p>Extends {@link IQboEntityService} with invoice-specific queries.
 * Implement this interface to provide alternative implementations
 * (e.g., cached, mock, batch-processing).
 */
public interface InvoiceService extends IQboEntityService<Invoice> {

    /** Returns all invoices with an outstanding balance (unpaid). */
    List<Invoice> findUnpaid(String realmId);

    /** Returns all invoices for a specific customer. */
    List<Invoice> findByCustomerId(String realmId, String customerId);

    /** Returns all invoices within the given date range (inclusive). */
    List<Invoice> findByDateRange(String realmId, LocalDate from, LocalDate to);

    /** Returns all overdue invoices (balance > 0 and due date passed). */
    List<Invoice> findOverdue(String realmId);
}
