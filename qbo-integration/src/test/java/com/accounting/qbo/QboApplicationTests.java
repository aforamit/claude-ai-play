package com.accounting.qbo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "qbo.client-id=test-client-id",
        "qbo.client-secret=test-client-secret",
        "qbo.sandbox=true"
})
class QboApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the Spring application context starts successfully
    }
}
