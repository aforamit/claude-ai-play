package com.accounting.receipt.model;

/**
 * Represents a single cash deposit record extracted from a receipt image.
 *
 * @param storeId               4-digit store number (e.g. "1176")
 * @param cashDepositDate       Printed deposit date on the receipt in MM-DD-YYYY format
 * @param cashBalanceDate       Hand-written balance/verification date in MM-DD-YYYY format;
 *                              empty string if not present or not legible
 * @param depositAccountNumber  Masked bank account number as printed on receipt (e.g. "*****1234");
 *                              empty string if not visible
 * @param totalCashDeposit      "Original Deposit" value from the transaction receipt.
 *                              For a single receipt with a daily breakdown table, this is the
 *                              same value on every row (the receipt total).
 *                              For multiple individual receipts, each has its own value.
 * @param amount                Individual cash deposit amount for this row/record
 */
public record DepositRecord(
        String storeId,
        String cashDepositDate,
        String cashBalanceDate,
        String depositAccountNumber,
        double totalCashDeposit,
        double amount) {

    public String toDisplayString() {
        String balDate = (cashBalanceDate == null || cashBalanceDate.isBlank()) ? "N/A" : cashBalanceDate;
        String acct    = (depositAccountNumber == null || depositAccountNumber.isBlank()) ? "N/A" : depositAccountNumber;
        return String.format(
                "Store: %-6s | Deposit: %s | Balance: %-12s | Acct: %-12s | Total: $%,.2f | Amount: $%,.2f",
                storeId, cashDepositDate, balDate, acct, totalCashDeposit, amount);
    }
}
