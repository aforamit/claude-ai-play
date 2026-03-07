package com.accounting.qbo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Represents a QuickBooks Online Deposit entity.
 *
 * <p>QBO API endpoint: {@code SELECT * FROM Deposit}
 *
 * @see <a href="https://developer.intuit.com/app/developer/qbo/docs/api/accounting/most-commonly-used/deposit">QBO Deposit API</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Deposit {

    @JsonProperty("Id")
    private String id;

    @JsonProperty("TxnDate")
    private LocalDate txnDate;

    @JsonProperty("DepositToAccountRef")
    private EntityRef depositToAccountRef;

    @JsonProperty("CurrencyRef")
    private EntityRef currencyRef;

    @JsonProperty("Line")
    private List<DepositLine> lines;

    @JsonProperty("TotalAmt")
    private BigDecimal totalAmount;

    @JsonProperty("PrivateNote")
    private String privateNote;

    @JsonProperty("GlobalTaxCalculation")
    private String globalTaxCalculation;

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public LocalDate getTxnDate() { return txnDate; }
    public void setTxnDate(LocalDate txnDate) { this.txnDate = txnDate; }

    public EntityRef getDepositToAccountRef() { return depositToAccountRef; }
    public void setDepositToAccountRef(EntityRef depositToAccountRef) { this.depositToAccountRef = depositToAccountRef; }

    public EntityRef getCurrencyRef() { return currencyRef; }
    public void setCurrencyRef(EntityRef currencyRef) { this.currencyRef = currencyRef; }

    public List<DepositLine> getLines() { return lines; }
    public void setLines(List<DepositLine> lines) { this.lines = lines; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getPrivateNote() { return privateNote; }
    public void setPrivateNote(String privateNote) { this.privateNote = privateNote; }

    public String getGlobalTaxCalculation() { return globalTaxCalculation; }
    public void setGlobalTaxCalculation(String globalTaxCalculation) { this.globalTaxCalculation = globalTaxCalculation; }
}
