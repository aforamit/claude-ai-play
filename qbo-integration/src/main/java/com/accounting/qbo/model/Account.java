package com.accounting.qbo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Represents a QuickBooks Online Account entity (Chart of Accounts).
 *
 * <p>QBO API endpoint: {@code SELECT * FROM Account}
 *
 * <p>Account types include: Bank, Accounts Receivable, Accounts Payable,
 * Credit Card, Other Current Asset, Fixed Asset, Other Asset, Income,
 * Cost of Goods Sold, Expense, Other Expense, Long Term Liability, Equity.
 *
 * @see <a href="https://developer.intuit.com/app/developer/qbo/docs/api/accounting/most-commonly-used/account">QBO Account API</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Account {

    @JsonProperty("Id")
    private String id;

    @JsonProperty("Name")
    private String name;

    @JsonProperty("FullyQualifiedName")
    private String fullyQualifiedName;

    @JsonProperty("Description")
    private String description;

    @JsonProperty("AccountType")
    private String accountType;

    @JsonProperty("AccountSubType")
    private String accountSubType;

    @JsonProperty("Classification")
    private String classification;   // Asset, Liability, Equity, Revenue, Expense

    @JsonProperty("CurrentBalance")
    private BigDecimal currentBalance;

    @JsonProperty("CurrentBalanceWithSubAccounts")
    private BigDecimal currentBalanceWithSubAccounts;

    @JsonProperty("CurrencyRef")
    private EntityRef currencyRef;

    @JsonProperty("ParentRef")
    private EntityRef parentRef;

    @JsonProperty("Active")
    private Boolean active;

    @JsonProperty("SubAccount")
    private Boolean subAccount;

    @JsonProperty("AcctNum")
    private String accountNumber;

    // ── Computed helpers ─────────────────────────────────────────────────────

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }

    public boolean isSubAccount() {
        return Boolean.TRUE.equals(subAccount);
    }

    public boolean isBalanceSheetAccount() {
        return "Asset".equals(classification)
                || "Liability".equals(classification)
                || "Equity".equals(classification);
    }

    public boolean isIncomeStatementAccount() {
        return "Revenue".equals(classification) || "Expense".equals(classification);
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFullyQualifiedName() { return fullyQualifiedName; }
    public void setFullyQualifiedName(String fullyQualifiedName) { this.fullyQualifiedName = fullyQualifiedName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }

    public String getAccountSubType() { return accountSubType; }
    public void setAccountSubType(String accountSubType) { this.accountSubType = accountSubType; }

    public String getClassification() { return classification; }
    public void setClassification(String classification) { this.classification = classification; }

    public BigDecimal getCurrentBalance() { return currentBalance; }
    public void setCurrentBalance(BigDecimal currentBalance) { this.currentBalance = currentBalance; }

    public BigDecimal getCurrentBalanceWithSubAccounts() { return currentBalanceWithSubAccounts; }
    public void setCurrentBalanceWithSubAccounts(BigDecimal currentBalanceWithSubAccounts) { this.currentBalanceWithSubAccounts = currentBalanceWithSubAccounts; }

    public EntityRef getCurrencyRef() { return currencyRef; }
    public void setCurrencyRef(EntityRef currencyRef) { this.currencyRef = currencyRef; }

    public EntityRef getParentRef() { return parentRef; }
    public void setParentRef(EntityRef parentRef) { this.parentRef = parentRef; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public Boolean getSubAccount() { return subAccount; }
    public void setSubAccount(Boolean subAccount) { this.subAccount = subAccount; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
}
