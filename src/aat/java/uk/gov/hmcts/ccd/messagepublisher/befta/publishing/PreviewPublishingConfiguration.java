package uk.gov.hmcts.ccd.messagepublisher.befta.publishing;

import java.net.URI;
import java.util.Map;

/** Validates explicit CI configuration before any case is created. */
public final class PreviewPublishingConfiguration {

    private final Map<String, String> environment;

    public PreviewPublishingConfiguration(Map<String, String> environment) {
        this.environment = Map.copyOf(environment);
        if (!"true".equals(environment.get("PUBLISHING_E2E_PUBLISHER_PUB_SUB_DOMAIN"))) {
            throw new IllegalArgumentException("Publishing E2E cannot run: publisher topic routing is unverified. "
                + "The checked-in preview defaults use queue mode. Set PUBLISHING_E2E_PUBLISHER_PUB_SUB_DOMAIN "
                + "from the deployed publisher's effective spring.jms.pub-sub-domain value. "
                + "This test does not change deployment routing; no case has been created.");
        }
        if (!required("PUBLISHING_E2E_PUBLISHER_DESTINATION").equals(required("PUBLISHING_E2E_TOPIC"))) {
            throw new IllegalArgumentException("Publishing E2E cannot run: the publisher destination does not "
                + "match PUBLISHING_E2E_TOPIC. Copy the deployed CCD_CASE_EVENTS_DESTINATION into "
                + "PUBLISHING_E2E_PUBLISHER_DESTINATION and provision the matching isolated route outside "
                + "this test. No case has been created.");
        }
        if (!"true".equals(required("PUBLISHING_E2E_ISOLATION_CONFIRMED"))) {
            throw new IllegalArgumentException("Confirm isolated publisher routing and a dedicated subscription "
                + "with PUBLISHING_E2E_ISOLATION_CONFIRMED=true before enabling this test");
        }
        URI publisher = httpsUrl(required("TEST_URL"));
        String suffix = ".preview.platform.hmcts.net";
        String host = publisher.getHost();
        if (!host.endsWith(suffix) || !host.substring(0, host.length() - suffix.length())
            .matches("ccd-message-publisher-pr-[0-9]+")) {
            throw new IllegalArgumentException("TEST_URL must identify a Message Publisher PR preview");
        }
        String release = host.substring(0, host.length() - suffix.length());
        if (!httpsUrl(required("PUBLISHING_E2E_DATA_STORE_URL")).getHost()
            .equals("ccd-data-store-api-" + host)) {
            throw new IllegalArgumentException("PUBLISHING_E2E_DATA_STORE_URL must belong to the same PR preview");
        }
        for (String key : new String[]{"PUBLISHING_E2E_TOPIC", "PUBLISHING_E2E_SUBSCRIPTION"}) {
            if (!required(key).startsWith(release + "-")) {
                throw new IllegalArgumentException(key + " must be scoped to the selected PR release");
            }
        }
        String endpoint = null;
        for (String part : required("PUBLISHING_E2E_CONNECTION_STRING").split(";")) {
            if (part.startsWith("Endpoint=")) {
                if (endpoint != null) {
                    throw new IllegalArgumentException("Service Bus connection string contains duplicate endpoints");
                }
                endpoint = part.substring("Endpoint=".length());
            }
        }
        try {
            URI broker = URI.create(endpoint == null ? "" : endpoint);
            if (!"sb".equals(broker.getScheme()) || broker.getHost() == null
                || !broker.getHost().endsWith("-preview.servicebus.windows.net")) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("PUBLISHING_E2E_CONNECTION_STRING must target a preview namespace");
        }
        required("PUBLISHING_E2E_USERNAME");
        required("PUBLISHING_E2E_PASSWORD");
    }

    String required(String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required environment variable: " + name);
        }
        return value;
    }

    private static URI httpsUrl(String value) {
        URI uri = URI.create(value);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
            || uri.getPort() != -1 || uri.getQuery() != null || uri.getFragment() != null
            || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("Preview URLs must be HTTPS origins without credentials or paths");
        }
        return uri;
    }
}
