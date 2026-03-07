package com.accounting.qbo.service.impl;

import com.accounting.qbo.client.QboApiClient;
import com.accounting.qbo.model.Invoice;
import com.accounting.qbo.service.InvoiceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Default implementation of {@link InvoiceService} using the QBO Query API.
 *
 * <p>All queries use QBO SQL syntax. See QBO docs for full WHERE clause options:
 * https://developer.intuit.com/app/developer/qbo/docs/develop/explore-the-quickbooks-online-api/data-queries
 */
@Service
public class InvoiceServiceImpl implements InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceServiceImpl.class);
    private static final String ENTITY = "Invoice";

    private final QboApiClient apiClient;

    public InvoiceServiceImpl(QboApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public List<Invoice> findAll(String realmId) {
        String q = "SELECT * FROM Invoice ORDERBY TxnDate DESC MAXRESULTS 1000";
        log.debug("findAll invoices for realmId={}", realmId);
        return apiClient.queryList(realmId, q, ENTITY, Invoice.class);
    }

    @Override
    public Optional<Invoice> findById(String realmId, String id) {
        log.debug("findById invoice id={} realmId={}", id, realmId);
        try {
            Invoice invoice = apiClient.readById(realmId, ENTITY.toLowerCase(), id, Invoice.class);
            return Optional.ofNullable(invoice);
        } catch (Exception e) {
            log.warn("Invoice not found: id={}, error={}", id, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<Invoice> query(String realmId, String whereClause) {
        String q = "SELECT * FROM Invoice WHERE " + whereClause;
        log.debug("Custom invoice query: {}", q);
        return apiClient.queryList(realmId, q, ENTITY, Invoice.class);
    }

    @Override
    public List<Invoice> findUnpaid(String realmId) {
        // Balance > 0 means there's an outstanding amount
        String q = "SELECT * FROM Invoice WHERE Balance > '0' ORDERBY DueDate ASC";
        log.debug("findUnpaid invoices for realmId={}", realmId);
        return apiClient.queryList(realmId, q, ENTITY, Invoice.class);
    }

    @Override
    public List<Invoice> findByCustomerId(String realmId, String customerId) {
        String q = "SELECT * FROM Invoice WHERE CustomerRef = '" + customerId +
                "' ORDERBY TxnDate DESC";
        log.debug("findByCustomerId={} realmId={}", customerId, realmId);
        return apiClient.queryList(realmId, q, ENTITY, Invoice.class);
    }

    @Override
    public List<Invoice> findByDateRange(String realmId, LocalDate from, LocalDate to) {
        String q = "SELECT * FROM Invoice WHERE TxnDate >= '" + from +
                "' AND TxnDate <= '" + to +
                "' ORDERBY TxnDate DESC";
        log.debug("findByDateRange from={} to={} realmId={}", from, to, realmId);
        return apiClient.queryList(realmId, q, ENTITY, Invoice.class);
    }

    @Override
    public List<Invoice> findOverdue(String realmId) {
        String today = LocalDate.now().toString();
        String q = "SELECT * FROM Invoice WHERE Balance > '0' AND DueDate < '" + today +
                "' ORDERBY DueDate ASC";
        log.debug("findOverdue invoices for realmId={}", realmId);
        return apiClient.queryList(realmId, q, ENTITY, Invoice.class);
    }
}
