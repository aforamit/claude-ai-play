package com.accounting.qbo.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for ReconciliationController.
 * Copies test fixture files to a temp directory and exercises the reconcile endpoint.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "qbo.client-id=test-client-id",
        "qbo.client-secret=test-client-secret",
        "qbo.sandbox=true"
})
class ReconciliationControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @TempDir
    static Path tempDir;

    private static String invoiceFilePath;
    private static String depositFilePath;
    private static String csvOutputPath;

    @BeforeAll
    static void copyFixtures(@TempDir Path dir) throws IOException {
        tempDir = dir;

        invoiceFilePath = copyResource("fixtures/invoices-recon.json", dir, "invoices.json")
                .toString().replace("\\", "/");
        depositFilePath = copyResource("fixtures/deposits-recon.json", dir, "deposits.json")
                .toString().replace("\\", "/");
        csvOutputPath = dir.resolve("reconciliation_output.csv").toString().replace("\\", "/");
    }

    // ── GET /api/reconcile ────────────────────────────────────────────────────

    @Test
    void reconcile_validFiles_returns200WithMatches() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/reconcile")
                        .param("invoiceFile", invoiceFilePath)
                        .param("depositFile", depositFilePath)
                        .param("csvOutput", csvOutputPath))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(response.path("data").isArray()).isTrue();
    }

    @Test
    void reconcile_returns2MatchedTransactions() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/reconcile")
                        .param("invoiceFile", invoiceFilePath)
                        .param("depositFile", depositFilePath)
                        .param("csvOutput", csvOutputPath))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.path("count").asInt()).isEqualTo(2);
        assertThat(response.path("data")).hasSize(2);
    }

    @Test
    void reconcile_matchedTransaction_hasInvoiceAndDepositFields() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/reconcile")
                        .param("invoiceFile", invoiceFilePath)
                        .param("depositFile", depositFilePath)
                        .param("csvOutput", csvOutputPath))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode firstMatch = response.path("data").get(0);

        assertThat(firstMatch.has("invoiceId")).isTrue();
        assertThat(firstMatch.has("depositId")).isTrue();
        assertThat(firstMatch.has("invoiceCustomer")).isTrue();
        assertThat(firstMatch.has("depositAccount")).isTrue();
    }

    @Test
    void reconcile_writesCsvOutputFile() throws Exception {
        Path outputPath = tempDir.resolve("it_output.csv");

        mockMvc.perform(get("/api/reconcile")
                        .param("invoiceFile", invoiceFilePath)
                        .param("depositFile", depositFilePath)
                        .param("csvOutput", outputPath.toString().replace("\\", "/")))
                .andExpect(status().isOk());

        assertThat(outputPath.toFile()).exists();
        assertThat(Files.size(outputPath)).isGreaterThan(0);
    }

    @Test
    void reconcile_missingRequiredParam_returns500() throws Exception {
        // @RequestParam is required; Spring MVC throws MissingServletRequestParameterException
        // which the generic GlobalExceptionHandler catches and returns 500
        mockMvc.perform(get("/api/reconcile")
                        .param("invoiceFile", invoiceFilePath))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void reconcile_nonExistentFiles_returns500() throws Exception {
        mockMvc.perform(get("/api/reconcile")
                        .param("invoiceFile", "nonexistent/invoices.json")
                        .param("depositFile", "nonexistent/deposits.json")
                        .param("csvOutput", tempDir.resolve("out.csv").toString()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void reconcile_responseHasTimestampAndSuccessFields() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/reconcile")
                        .param("invoiceFile", invoiceFilePath)
                        .param("depositFile", depositFilePath)
                        .param("csvOutput", tempDir.resolve("ts_out.csv").toString()
                                .replace("\\", "/")))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(response.has("timestamp")).isTrue();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Path copyResource(String resourcePath, Path targetDir, String targetName)
            throws IOException {
        try (InputStream is = ReconciliationControllerIT.class
                .getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            Path target = targetDir.resolve(targetName);
            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        }
    }
}
