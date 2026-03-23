package com.accounting.qbo.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for CsvDepositController.
 * Starts the full Spring context and exercises the generate endpoint
 * using the actual deposits.csv and prod data files.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "qbo.client-id=test-client-id",
        "qbo.client-secret=test-client-secret",
        "qbo.sandbox=true"
})
class CsvDepositControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @TempDir
    static Path tempOutputDir;

    // ── GET /api/csv/deposits/generate ────────────────────────────────────────

    @Test
    void generate_defaultPaths_returns200WithDepositsJsonInList() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/csv/deposits/generate"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(response.path("data").isArray()).isTrue();
        assertThat(response.path("data").get(0).asText()).isEqualTo("deposits.json");
    }

    @Test
    void generate_defaultPaths_countIsOne() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/csv/deposits/generate"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.path("count").asInt()).isEqualTo(1);
    }

    @Test
    void generate_responseHasTimestamp() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/csv/deposits/generate"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.has("timestamp")).isTrue();
    }

    @Test
    void generate_customOutputDir_writesDepositsJsonToThatDir() throws Exception {
        String customOutput = tempOutputDir.toString().replace("\\", "/");

        MvcResult result = mockMvc.perform(get("/api/csv/deposits/generate")
                        .param("outputDir", customOutput))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.path("success").asBoolean()).isTrue();

        // Verify file was actually written to custom dir
        assertThat(Files.exists(tempOutputDir.resolve("deposits.json"))).isTrue();
    }

    @Test
    void generate_invalidCsvPath_returns500WithErrorMessage() throws Exception {
        mockMvc.perform(get("/api/csv/deposits/generate")
                        .param("csvPath", "nonexistent/path/deposits.csv"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void generate_producedJsonIsValidArray() throws Exception {
        mockMvc.perform(get("/api/csv/deposits/generate"))
                .andExpect(status().isOk());

        // Verify the deposits.json output is a valid JSON array
        Path depositsFile = Path.of("data/test/deposits.json");
        if (Files.exists(depositsFile)) {
            JsonNode depositsNode = objectMapper.readTree(depositsFile.toFile());
            assertThat(depositsNode.isArray()).isTrue();
        }
    }

    @Test
    void generate_producedDeposits_havePrivateNote() throws Exception {
        mockMvc.perform(get("/api/csv/deposits/generate"))
                .andExpect(status().isOk());

        Path depositsFile = Path.of("data/test/deposits.json");
        if (Files.exists(depositsFile)) {
            JsonNode deposits = objectMapper.readTree(depositsFile.toFile());
            for (JsonNode deposit : deposits) {
                assertThat(deposit.path("PrivateNote").asText())
                        .isEqualTo("CashDepositAutomation.AI");
            }
        }
    }

    @Test
    void generate_customCsvAndOutputDir_worksEndToEnd() throws Exception {
        // Use the actual deposits.csv but write to temp dir
        String customOutput = tempOutputDir.toString().replace("\\", "/");

        mockMvc.perform(get("/api/csv/deposits/generate")
                        .param("csvPath", "data/receipts/deposits.csv")
                        .param("outputDir", customOutput))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
