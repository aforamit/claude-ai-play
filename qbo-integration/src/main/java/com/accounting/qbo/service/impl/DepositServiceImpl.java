package com.accounting.qbo.service.impl;

import com.accounting.qbo.client.QboApiClient;
import com.accounting.qbo.model.Deposit;
import com.accounting.qbo.service.DepositService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Default implementation of {@link DepositService} using the QBO Query API.
 */
@Service
public class DepositServiceImpl implements DepositService {

    private static final Logger log = LoggerFactory.getLogger(DepositServiceImpl.class);
    private static final String ENTITY = "Deposit";

    private final QboApiClient apiClient;

    public DepositServiceImpl(QboApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public List<Deposit> findAll(String realmId) {
        String q = "SELECT * FROM Deposit ORDERBY TxnDate DESC MAXRESULTS 1000";
        log.debug("findAll deposits for realmId={}", realmId);
        return apiClient.queryList(realmId, q, ENTITY, Deposit.class);
    }

    @Override
    public Optional<Deposit> findById(String realmId, String id) {
        log.debug("findById deposit id={} realmId={}", id, realmId);
        try {
            Deposit deposit = apiClient.readById(realmId, ENTITY.toLowerCase(), id, Deposit.class);
            return Optional.ofNullable(deposit);
        } catch (Exception e) {
            log.warn("Deposit not found: id={}, error={}", id, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<Deposit> query(String realmId, String whereClause) {
        String q = "SELECT * FROM Deposit WHERE " + whereClause;
        log.debug("Custom deposit query: {}", q);
        return apiClient.queryList(realmId, q, ENTITY, Deposit.class);
    }

    @Override
    public List<Deposit> findByDateRange(String realmId, LocalDate from, LocalDate to) {
        String q = "SELECT * FROM Deposit WHERE TxnDate >= '" + from +
                "' AND TxnDate <= '" + to +
                "' ORDERBY TxnDate DESC";
        log.debug("findByDateRange from={} to={} realmId={}", from, to, realmId);
        return apiClient.queryList(realmId, q, ENTITY, Deposit.class);
    }

    @Override
    public List<Deposit> findByAccountId(String realmId, String accountId) {
        // DepositToAccountRef is the target bank/clearing account
        String q = "SELECT * FROM Deposit WHERE DepositToAccountRef = '" + accountId +
                "' ORDERBY TxnDate DESC";
        log.debug("findByAccountId={} realmId={}", accountId, realmId);
        return apiClient.queryList(realmId, q, ENTITY, Deposit.class);
    }
}
