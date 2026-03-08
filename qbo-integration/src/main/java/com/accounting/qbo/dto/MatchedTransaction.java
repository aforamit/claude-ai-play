package com.accounting.qbo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a matched pair of Invoice and Deposit, linked via
 * a Deposit line with Description "ACCOUNTABLE CASH" whose
 * LinkedTxn.TxnId equals the Invoice Id.
 */
public class MatchedTransaction {

    // ── Invoice fields ────────────────────────────────────────────────────────
    private String invoiceId;
    private String invoiceDocNumber;
    private LocalDate invoiceTxnDate;
    private LocalDate invoiceDueDate;
    private String invoiceCustomer;
    private String invoiceLocation;
    private BigDecimal invoiceAccountableCash;

    // ── Deposit fields ────────────────────────────────────────────────────────
    private String depositId;
    private LocalDate depositTxnDate;
    private BigDecimal depositTotalAmount;
    private String depositAccount;
    private BigDecimal accountableCashAmount;

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getInvoiceId() { return invoiceId; }
    public void setInvoiceId(String invoiceId) { this.invoiceId = invoiceId; }

    public String getInvoiceDocNumber() { return invoiceDocNumber; }
    public void setInvoiceDocNumber(String invoiceDocNumber) { this.invoiceDocNumber = invoiceDocNumber; }

    public LocalDate getInvoiceTxnDate() { return invoiceTxnDate; }
    public void setInvoiceTxnDate(LocalDate invoiceTxnDate) { this.invoiceTxnDate = invoiceTxnDate; }

    public LocalDate getInvoiceDueDate() { return invoiceDueDate; }
    public void setInvoiceDueDate(LocalDate invoiceDueDate) { this.invoiceDueDate = invoiceDueDate; }

    public String getInvoiceCustomer() { return invoiceCustomer; }
    public void setInvoiceCustomer(String invoiceCustomer) { this.invoiceCustomer = invoiceCustomer; }

    public String getInvoiceLocation() { return invoiceLocation; }
    public void setInvoiceLocation(String invoiceLocation) { this.invoiceLocation = invoiceLocation; }

    public BigDecimal getInvoiceAccountableCash() { return invoiceAccountableCash; }
    public void setInvoiceAccountableCash(BigDecimal invoiceAccountableCash) { this.invoiceAccountableCash = invoiceAccountableCash; }

    public String getDepositId() { return depositId; }
    public void setDepositId(String depositId) { this.depositId = depositId; }

    public LocalDate getDepositTxnDate() { return depositTxnDate; }
    public void setDepositTxnDate(LocalDate depositTxnDate) { this.depositTxnDate = depositTxnDate; }

    public BigDecimal getDepositTotalAmount() { return depositTotalAmount; }
    public void setDepositTotalAmount(BigDecimal depositTotalAmount) { this.depositTotalAmount = depositTotalAmount; }

    public String getDepositAccount() { return depositAccount; }
    public void setDepositAccount(String depositAccount) { this.depositAccount = depositAccount; }

    public BigDecimal getAccountableCashAmount() { return accountableCashAmount; }
    public void setAccountableCashAmount(BigDecimal accountableCashAmount) { this.accountableCashAmount = accountableCashAmount; }
}
