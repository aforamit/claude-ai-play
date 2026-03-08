package com.accounting.qbo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Represents a line item within a QBO Deposit.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DepositLine {

    @JsonProperty("Description")
    private String description;

    @JsonProperty("Amount")
    private BigDecimal amount;

    @JsonProperty("DetailType")
    private String detailType;

    @JsonProperty("DepositLineDetail")
    private DepositLineDetail depositLineDetail;

    @JsonProperty("LinkedTxn")
    private java.util.List<LinkedTransaction> linkedTransactions;

    // ── Nested types ─────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DepositLineDetail {

        @JsonProperty("AccountRef")
        private EntityRef accountRef;

        @JsonProperty("Entity")
        private EntityWrapper entity;

        @JsonProperty("CheckNum")
        private String checkNum;

        @JsonProperty("PaymentMethodRef")
        private EntityRef paymentMethodRef;

        public EntityRef getAccountRef() { return accountRef; }
        public void setAccountRef(EntityRef accountRef) { this.accountRef = accountRef; }
        public EntityWrapper getEntity() { return entity; }
        public void setEntity(EntityWrapper entity) { this.entity = entity; }
        public String getCheckNum() { return checkNum; }
        public void setCheckNum(String checkNum) { this.checkNum = checkNum; }
        public EntityRef getPaymentMethodRef() { return paymentMethodRef; }
        public void setPaymentMethodRef(EntityRef paymentMethodRef) { this.paymentMethodRef = paymentMethodRef; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EntityWrapper {
        @JsonProperty("EntityRef")
        private EntityRef entityRef;

        @JsonProperty("Type")
        private String type;

        public EntityRef getEntityRef() { return entityRef; }
        public void setEntityRef(EntityRef entityRef) { this.entityRef = entityRef; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LinkedTransaction {
        @JsonProperty("TxnId")
        private String txnId;

        @JsonProperty("TxnType")
        private String txnType;

        public String getTxnId() { return txnId; }
        public void setTxnId(String txnId) { this.txnId = txnId; }
        public String getTxnType() { return txnType; }
        public void setTxnType(String txnType) { this.txnType = txnType; }
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getDetailType() { return detailType; }
    public void setDetailType(String detailType) { this.detailType = detailType; }
    public DepositLineDetail getDepositLineDetail() { return depositLineDetail; }
    public void setDepositLineDetail(DepositLineDetail depositLineDetail) { this.depositLineDetail = depositLineDetail; }
    public java.util.List<LinkedTransaction> getLinkedTransactions() { return linkedTransactions; }
    public void setLinkedTransactions(java.util.List<LinkedTransaction> linkedTransactions) { this.linkedTransactions = linkedTransactions; }
}
