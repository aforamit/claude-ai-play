package com.accounting.qbo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Represents a line item within a QBO Invoice.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InvoiceLine {

    @JsonProperty("Id")
    private String id;

    @JsonProperty("LineNum")
    private Integer lineNum;

    @JsonProperty("Description")
    private String description;

    @JsonProperty("Amount")
    private BigDecimal amount;

    @JsonProperty("DetailType")
    private String detailType;

    @JsonProperty("SalesItemLineDetail")
    private SalesItemDetail salesItemDetail;

    @JsonProperty("DiscountLineDetail")
    private DiscountDetail discountDetail;

    // ── Nested types ─────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SalesItemDetail {
        @JsonProperty("ItemRef")
        private EntityRef itemRef;

        @JsonProperty("UnitPrice")
        private BigDecimal unitPrice;

        @JsonProperty("Qty")
        private BigDecimal quantity;

        @JsonProperty("TaxCodeRef")
        private EntityRef taxCodeRef;

        public EntityRef getItemRef() { return itemRef; }
        public void setItemRef(EntityRef itemRef) { this.itemRef = itemRef; }
        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
        public EntityRef getTaxCodeRef() { return taxCodeRef; }
        public void setTaxCodeRef(EntityRef taxCodeRef) { this.taxCodeRef = taxCodeRef; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DiscountDetail {
        @JsonProperty("PercentBased")
        private Boolean percentBased;

        @JsonProperty("DiscountPercent")
        private BigDecimal discountPercent;

        public Boolean getPercentBased() { return percentBased; }
        public void setPercentBased(Boolean percentBased) { this.percentBased = percentBased; }
        public BigDecimal getDiscountPercent() { return discountPercent; }
        public void setDiscountPercent(BigDecimal discountPercent) { this.discountPercent = discountPercent; }
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Integer getLineNum() { return lineNum; }
    public void setLineNum(Integer lineNum) { this.lineNum = lineNum; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getDetailType() { return detailType; }
    public void setDetailType(String detailType) { this.detailType = detailType; }
    public SalesItemDetail getSalesItemDetail() { return salesItemDetail; }
    public void setSalesItemDetail(SalesItemDetail salesItemDetail) { this.salesItemDetail = salesItemDetail; }
    public DiscountDetail getDiscountDetail() { return discountDetail; }
    public void setDiscountDetail(DiscountDetail discountDetail) { this.discountDetail = discountDetail; }
}
