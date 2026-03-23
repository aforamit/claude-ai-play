package com.accounting.qbo.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QboPropertiesTest {

    @Test
    void getBaseUrl_sandboxTrue_returnsSandboxUrl() {
        QboProperties props = new QboProperties();
        props.setSandbox(true);
        assertThat(props.getBaseUrl()).isEqualTo("https://sandbox-quickbooks.api.intuit.com");
    }

    @Test
    void getBaseUrl_sandboxFalse_returnsProductionUrl() {
        QboProperties props = new QboProperties();
        props.setSandbox(false);
        assertThat(props.getBaseUrl()).isEqualTo("https://quickbooks.api.intuit.com");
    }

    @Test
    void getApiVersion_returnsV3() {
        QboProperties props = new QboProperties();
        assertThat(props.getApiVersion()).isEqualTo("v3");
    }

    @Test
    void getMinorVersion_returns65() {
        QboProperties props = new QboProperties();
        assertThat(props.getMinorVersion()).isEqualTo("65");
    }

    @Test
    void getScopesAsString_defaultScopes_returnsSpaceSeparated() {
        QboProperties props = new QboProperties();
        assertThat(props.getScopesAsString()).isEqualTo("com.intuit.quickbooks.accounting");
    }

    @Test
    void getScopesAsString_multipleScopes_joinedWithSpace() {
        QboProperties props = new QboProperties();
        props.setScopes(List.of("com.intuit.quickbooks.accounting", "com.intuit.quickbooks.payment"));
        assertThat(props.getScopesAsString())
                .isEqualTo("com.intuit.quickbooks.accounting com.intuit.quickbooks.payment");
    }

    @Test
    void defaultRedirectUri_isLocalhost() {
        QboProperties props = new QboProperties();
        assertThat(props.getRedirectUri()).isEqualTo("http://localhost:8080/api/auth/callback");
    }

    @Test
    void defaultSandbox_isTrue() {
        QboProperties props = new QboProperties();
        assertThat(props.isSandbox()).isTrue();
    }

    @Test
    void intuitAuthUrl_constant_isCorrect() {
        assertThat(QboProperties.INTUIT_AUTH_URL)
                .isEqualTo("https://appcenter.intuit.com/connect/oauth2");
    }

    @Test
    void intuitTokenUrl_constant_isCorrect() {
        assertThat(QboProperties.INTUIT_TOKEN_URL)
                .isEqualTo("https://oauth.platform.intuit.com/oauth2/v1/tokens/bearer");
    }

    @Test
    void settersAndGetters_workCorrectly() {
        QboProperties props = new QboProperties();
        props.setClientId("my-client");
        props.setClientSecret("my-secret");
        props.setRealmId("realm123");
        props.setRedirectUri("http://example.com/callback");

        assertThat(props.getClientId()).isEqualTo("my-client");
        assertThat(props.getClientSecret()).isEqualTo("my-secret");
        assertThat(props.getRealmId()).isEqualTo("realm123");
        assertThat(props.getRedirectUri()).isEqualTo("http://example.com/callback");
    }
}
