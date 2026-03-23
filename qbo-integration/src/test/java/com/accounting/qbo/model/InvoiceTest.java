package com.accounting.qbo.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceTest {

    @Test
    void isUnpaid_balanceGreaterThanZero_returnsTrue() {
        Invoice invoice = new Invoice();
        invoice.setBalance(new BigDecimal("100.00"));
        assertThat(invoice.isUnpaid()).isTrue();
    }

    @Test
    void isUnpaid_balanceEqualToZero_returnsFalse() {
        Invoice invoice = new Invoice();
        invoice.setBalance(BigDecimal.ZERO);
        assertThat(invoice.isUnpaid()).isFalse();
    }

    @Test
    void isUnpaid_balanceNull_returnsFalse() {
        Invoice invoice = new Invoice();
        // balance is null by default
        assertThat(invoice.isUnpaid()).isFalse();
    }

    @Test
    void isUnpaid_negativeBalance_returnsFalse() {
        Invoice invoice = new Invoice();
        invoice.setBalance(new BigDecimal("-5.00"));
        assertThat(invoice.isUnpaid()).isFalse();
    }

    @Test
    void isPaid_balanceZero_returnsTrue() {
        Invoice invoice = new Invoice();
        invoice.setBalance(BigDecimal.ZERO);
        assertThat(invoice.isPaid()).isTrue();
    }

    @Test
    void isPaid_balancePositive_returnsFalse() {
        Invoice invoice = new Invoice();
        invoice.setBalance(new BigDecimal("50.00"));
        assertThat(invoice.isPaid()).isFalse();
    }

    @Test
    void isPaid_balanceNull_returnsTrue() {
        Invoice invoice = new Invoice();
        assertThat(invoice.isPaid()).isTrue();
    }

    @Test
    void gettersAndSetters_workCorrectly() {
        Invoice invoice = new Invoice();
        invoice.setId("INV-123");
        invoice.setDocNumber("260301-1174");
        invoice.setPrivateNote("Test note");

        assertThat(invoice.getId()).isEqualTo("INV-123");
        assertThat(invoice.getDocNumber()).isEqualTo("260301-1174");
        assertThat(invoice.getPrivateNote()).isEqualTo("Test note");
    }

    @Test
    void isUnpaid_smallPositiveBalance_returnsTrue() {
        Invoice invoice = new Invoice();
        invoice.setBalance(new BigDecimal("0.01"));
        assertThat(invoice.isUnpaid()).isTrue();
    }
}
