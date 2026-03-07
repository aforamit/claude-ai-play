package com.accounting.qbo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Represents a QuickBooks Online Invoice entity.
 *
 * <p>QBO API endpoint: {@code SELECT * FROM Invoice}
 *
 * @see <a href="https://developer.intuit.com/app/developer/qbo/docs/api/accounting/most-commonly-used/invoice">QBO Invoice API</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Invoice {

    @JsonProperty("Id")
    private String id;

    @JsonProperty("DocNumber")
    private String docNumber;

    @JsonProperty("TxnDate")
    private LocalDate txnDate;

    @JsonProperty("DueDate")
    private LocalDate dueDate;

    @JsonProperty("CustomerRef")
    private EntityRef customerRef;

    @JsonProperty("CurrencyRef")
    private EntityRef currencyRef;

    @JsonProperty("Line")
    private List<InvoiceLine> lines;

    @JsonProperty("TotalAmt")
    private BigDecimal totalAmount;

    @JsonProperty("Balance")
    private BigDecimal balance;

    @JsonProperty("EmailStatus")
    private String emailStatus;

    @JsonProperty("PrintStatus")
    private String printStatus;

    @JsonProperty("PrivateNote")
    private String privateNote;

    @JsonProperty("CustomerMemo")
    private Memo customerMemo;

    // ── Computed helpers ─────────────────────────────────────────────────────

    /** Returns true if the invoice has an outstanding balance. */
    public boolean isUnpaid() {
        return balance != null && balance.compareTo(BigDecimal.ZERO) > 0;
    }

    /** Returns true if fully paid. */
    public boolean isPaid() {
        return !isUnpaid();
    }

    // ── Nested types ─────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Memo {
        @JsonProperty("value")
        private String value;

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDocNumber() { return docNumber; }
    public void setDocNumber(String docNumber) { this.docNumber = docNumber; }

    public LocalDate getTxnDate() { return txnDate; }
    public void setTxnDate(LocalDate txnDate) { this.txnDate = txnDate; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public EntityRef getCustomerRef() { return customerRef; }
    public void setCustomerRef(EntityRef customerRef) { this.customerRef = customerRef; }

    public EntityRef getCurrencyRef() { return currencyRef; }
    public void setCurrencyRef(EntityRef currencyRef) { this.currencyRef = currencyRef; }

    public List<InvoiceLine> getLines() { return lines; }
    public void setLines(List<InvoiceLine> lines) { this.lines = lines; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public String getEmailStatus() { return emailStatus; }
    public void setEmailStatus(String emailStatus) { this.emailStatus = emailStatus; }

    public String getPrintStatus() { return printStatus; }
    public void setPrintStatus(String printStatus) { this.printStatus = printStatus; }

    public String getPrivateNote() { return privateNote; }
    public void setPrivateNote(String privateNote) { this.privateNote = privateNote; }

    public Memo getCustomerMemo() { return customerMemo; }
    public void setCustomerMemo(Memo customerMemo) { this.customerMemo = customerMemo; }
}
