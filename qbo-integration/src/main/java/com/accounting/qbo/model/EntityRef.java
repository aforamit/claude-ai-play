package com.accounting.qbo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Reusable QBO reference object used throughout the API responses.
 * Example: CustomerRef, AccountRef, CurrencyRef.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EntityRef {

    @JsonProperty("value")
    private String id;

    @JsonProperty("name")
    private String name;

    public EntityRef() {}

    public EntityRef(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @Override
    public String toString() {
        return name + " (id=" + id + ")";
    }
}
