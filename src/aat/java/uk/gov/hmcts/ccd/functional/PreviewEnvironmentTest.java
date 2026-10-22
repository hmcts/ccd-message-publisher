package uk.gov.hmcts.ccd.functional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreviewEnvironmentTest {
    private static final String CREDENTIALS = ";SharedAccessKeyName=test-policy;SharedAccessKey=secret-test-key";

    @Test
    void acceptsPreviewEndpointWithOrWithoutTrailingSlash() {
        for (String suffix : new String[] {"", "/"}) {
            assertThatCode(() -> PreviewEnvironment.validatePreviewBroker(
                "Endpoint=sb://ccd-servicebus-preview.servicebus.windows.net" + suffix + CREDENTIALS))
                .doesNotThrowAnyException();
        }
    }

    @Test
    void acceptsDeployedPreviewEndpointWithOrWithoutTrailingSlash() {
        for (String suffix : new String[] {"", "/"}) {
            assertThatCode(() -> PreviewEnvironment.validatePreviewBroker(
                "Endpoint=sb://ccd-sb-preview.servicebus.windows.net" + suffix + CREDENTIALS))
                .doesNotThrowAnyException();
        }
    }

    @Test
    void reportsUnexpectedHostWithoutCredentials() {
        assertThatThrownBy(() -> PreviewEnvironment.validatePreviewBroker(
            "Endpoint=sb://ccd-servicebus-aat.servicebus.windows.net/" + CREDENTIALS))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("detected host: ccd-servicebus-aat.servicebus.windows.net")
            .hasMessageContaining("expected Endpoint=sb://ccd-servicebus-preview.servicebus.windows.net/")
            .hasMessageNotContaining("secret-test-key")
            .hasMessageNotContaining("test-policy");
    }

    @Test
    void rejectsNonPreviewAndLookalikeHosts() {
        for (String host : new String[] {"ccd-sb-aat.servicebus.windows.net",
            "ccd-sb-prod.servicebus.windows.net", "ccd-sb-preview.servicebus.windows.net.example.com"}) {
            assertThatThrownBy(() -> PreviewEnvironment.validatePreviewBroker(
                "Endpoint=sb://" + host + "/" + CREDENTIALS))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("detected host: " + host)
                .hasMessageNotContaining("secret-test-key")
                .hasMessageNotContaining("test-policy");
        }
    }

    @Test
    void rejectsSurroundingWhitespaceWhileReportingHost() {
        assertThatThrownBy(() -> PreviewEnvironment.validatePreviewBroker(
            " Endpoint=sb://ccd-servicebus-preview.servicebus.windows.net/ " + CREDENTIALS))
            .hasMessageContaining("detected host: ccd-servicebus-preview.servicebus.windows.net")
            .hasMessageContaining("no surrounding whitespace")
            .hasMessageNotContaining("secret-test-key")
            .hasMessageNotContaining("test-policy");
    }

    @Test
    void reportsMissingOrMalformedEndpointWithoutEchoingInput() {
        for (String endpoint : new String[] {"", "Endpoint=sb://secret-test-key[invalid", "Endpoint=garbage"}) {
            assertThatThrownBy(() -> PreviewEnvironment.validatePreviewBroker(endpoint + CREDENTIALS))
                .hasMessageContaining("detected host: missing or malformed Endpoint")
                .hasMessageNotContaining("secret-test-key")
                .hasMessageNotContaining("test-policy")
                .hasMessageNotContaining("garbage");
        }
    }
}
