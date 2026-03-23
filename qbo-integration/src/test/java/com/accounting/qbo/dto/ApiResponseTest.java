package com.accounting.qbo.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void ofList_successTrue_countMatchesList() {
        List<String> items = List.of("a", "b", "c");
        ApiResponse<List<String>> response = ApiResponse.ofList(items);

        assertThat(response.success()).isTrue();
        assertThat(response.count()).isEqualTo(3);
        assertThat(response.data()).isEqualTo(items);
        assertThat(response.message()).isNull();
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void ofList_emptyList_countIsZero() {
        ApiResponse<List<String>> response = ApiResponse.ofList(List.of());
        assertThat(response.success()).isTrue();
        assertThat(response.count()).isEqualTo(0);
        assertThat(response.data()).isEmpty();
    }

    @Test
    void ofOne_successTrue_countIsOne() {
        String data = "singleItem";
        ApiResponse<String> response = ApiResponse.ofOne(data);

        assertThat(response.success()).isTrue();
        assertThat(response.count()).isEqualTo(1);
        assertThat(response.data()).isEqualTo("singleItem");
        assertThat(response.message()).isNull();
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void ok_successTrue_messageSet_dataNull() {
        ApiResponse<Void> response = ApiResponse.ok("Operation succeeded");

        assertThat(response.success()).isTrue();
        assertThat(response.message()).isEqualTo("Operation succeeded");
        assertThat(response.data()).isNull();
        assertThat(response.count()).isNull();
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void error_successFalse_messageSet_dataNull() {
        ApiResponse<Void> response = ApiResponse.error("Something went wrong");

        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("Something went wrong");
        assertThat(response.data()).isNull();
        assertThat(response.count()).isNull();
        assertThat(response.timestamp()).isNotNull();
    }

    @Test
    void ofList_withObjects_preservesData() {
        MatchedTransaction m = new MatchedTransaction();
        m.setInvoiceId("INV-001");
        List<MatchedTransaction> list = List.of(m);
        ApiResponse<List<MatchedTransaction>> response = ApiResponse.ofList(list);

        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).getInvoiceId()).isEqualTo("INV-001");
    }
}
