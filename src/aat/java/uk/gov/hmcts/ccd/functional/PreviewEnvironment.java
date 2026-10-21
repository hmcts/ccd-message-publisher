package uk.gov.hmcts.ccd.functional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves the selected preview's resources without requiring additional CI secrets. */
final class PreviewEnvironment implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final String namespace;
    private Process portForward;
    private Connection database;
    private String topic;
    private String brokerConnectionString;

    private PreviewEnvironment(String namespace) {
        this.namespace = namespace;
    }

    static PreviewEnvironment connect() throws Exception {
        URI target = URI.create(requiredEnvironment("TEST_URL"));
        if (!"https".equals(target.getScheme()) || target.getHost() == null
            || !target.getHost().matches("ccd-message-publisher-[a-z0-9-]+\\.preview\\.platform\\.hmcts\\.net")
            || target.getUserInfo() != null || (target.getPort() != -1 && target.getPort() != 443)) {
            throw new IllegalArgumentException("Publishing functional tests require a CCD publisher preview URL");
        }
        String namespace = requiredEnvironment("TEAM_NAMESPACE");
        if (!namespace.matches("[a-z0-9][a-z0-9-]*")) {
            throw new IllegalArgumentException("TEAM_NAMESPACE must be a Kubernetes namespace");
        }
        PreviewEnvironment preview = new PreviewEnvironment(namespace);
        try {
            preview.resolve(target.getHost());
            return preview;
        } catch (Exception failure) {
            preview.close();
            throw failure;
        }
    }

    Connection database() {
        return database;
    }

    String topic() {
        return topic;
    }

    String brokerConnectionString() {
        return brokerConnectionString;
    }

    private void resolve(String host) throws Exception {
        List<JsonNode> matchingIngresses = new ArrayList<>();
        for (JsonNode ingress : resource("ingress").path("items")) {
            for (JsonNode rule : ingress.path("spec").path("rules")) {
                if (host.equals(rule.path("host").asText())) {
                    matchingIngresses.add(ingress);
                }
            }
        }
        if (matchingIngresses.size() != 1) {
            throw new IllegalStateException("Expected one preview ingress matching TEST_URL in TEAM_NAMESPACE");
        }
        JsonNode ingress = matchingIngresses.getFirst();
        String release = ingress.path("metadata").path("annotations").path("meta.helm.sh/release-name").asText();
        if (!release.matches("ccd-message-publisher-[a-z0-9-]+")) {
            throw new IllegalStateException("The selected ingress is not a CCD publisher preview release");
        }
        String service = "";
        for (JsonNode rule : ingress.path("spec").path("rules")) {
            if (host.equals(rule.path("host").asText())) {
                service = rule.path("http").path("paths").get(0).path("backend").path("service").path("name").asText();
            }
        }
        JsonNode selector = resource("service/" + service).path("spec").path("selector");
        List<String> labels = new ArrayList<>();
        selector.fields().forEachRemaining(entry -> labels.add(entry.getKey() + "=" + entry.getValue().asText()));
        if (labels.isEmpty()) {
            throw new IllegalStateException("The preview publisher service has no pod selector");
        }
        JsonNode pods = resource("pods", "-l", String.join(",", labels));
        JsonNode publisher = null;
        for (JsonNode pod : pods.path("items")) {
            if ("Running".equals(pod.path("status").path("phase").asText())) {
                for (JsonNode container : pod.path("spec").path("containers")) {
                    for (JsonNode variable : container.path("env")) {
                        if ("CCD_CASE_EVENTS_DESTINATION".equals(variable.path("name").asText())) {
                            publisher = container;
                        }
                    }
                }
            }
        }
        if (publisher == null) {
            throw new IllegalStateException("No running publisher container found for the preview ingress");
        }
        topic = environment(publisher, "CCD_CASE_EVENTS_DESTINATION");
        String databaseHost = environment(publisher, "DATA_STORE_DB_HOST");
        String databaseName = environment(publisher, "DATA_STORE_DB_NAME");
        if (!databaseHost.equals(release + "-postgresql")
            || !databaseName.equals("data-store")) {
            throw new IllegalStateException("Refusing to seed resources outside the selected preview release");
        }
        // The topic may be shared within preview; each scenario uses its own filtered subscription.
        brokerConnectionString = environment(publisher, "SERVICE_BUS_CONNECTION_STRING");
        boolean previewBroker = List.of(brokerConnectionString.split(";")).stream().anyMatch(part ->
            part.matches("Endpoint=sb://ccd-servicebus-preview\\.servicebus\\.windows\\.net/?"));
        if (!previewBroker) {
            throw new IllegalStateException("Refusing to use a broker outside the preview Service Bus namespace");
        }
        int port = forwardDatabase(databaseHost);
        database = DriverManager.getConnection(
            "jdbc:postgresql://127.0.0.1:" + port + "/" + databaseName
                + "?connectTimeout=10&socketTimeout=15&sslmode=disable",
            environment(publisher, "DATA_STORE_DB_USERNAME"), environment(publisher, "DATA_STORE_DB_PASSWORD"));
    }

    private String environment(JsonNode container, String name) throws Exception {
        for (JsonNode variable : container.path("env")) {
            if (name.equals(variable.path("name").asText())) {
                if (variable.has("value")) {
                    return variable.path("value").asText();
                }
                JsonNode reference = variable.path("valueFrom").path("secretKeyRef");
                String encoded = resource("secret/" + reference.path("name").asText()).path("data")
                    .path(reference.path("key").asText()).asText();
                if (!encoded.isEmpty()) {
                    return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IllegalStateException("Missing preview publisher setting: " + name);
    }

    private int forwardDatabase(String service) throws Exception {
        portForward = new ProcessBuilder(command("port-forward", "service/" + service, ":5432",
                                                  "--address=127.0.0.1"))
            .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        CompletableFuture<String> line = CompletableFuture.supplyAsync(() -> {
            try {
                return new BufferedReader(new InputStreamReader(portForward.getInputStream(), StandardCharsets.UTF_8))
                    .readLine();
            } catch (Exception failure) {
                throw new IllegalStateException("Unable to read database port-forward status");
            }
        });
        String status = line.get(30, TimeUnit.SECONDS);
        Matcher port = Pattern.compile("Forwarding from 127\\.0\\.0\\.1:(\\d+) -> 5432").matcher(
            status == null ? "" : status);
        if (!port.find()) {
            throw new IllegalStateException("Unable to start preview database port-forward");
        }
        // Drain kubectl's connection messages so its output pipe cannot fill during polling.
        CompletableFuture.runAsync(() -> {
            try {
                portForward.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            } catch (Exception ignored) {
                // Closing the fixture stops kubectl and its output stream.
            }
        });
        return Integer.parseInt(port.group(1));
    }

    private JsonNode resource(String name, String... arguments) throws Exception {
        List<String> args = new ArrayList<>(List.of("get", name, "-o", "json", "--request-timeout=20s"));
        args.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command(args.toArray(String[]::new)))
            .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            CompletableFuture<byte[]> output = CompletableFuture.supplyAsync(() -> {
                try {
                    return process.getInputStream().readAllBytes();
                } catch (Exception failure) {
                    throw new IllegalStateException("Unable to read Kubernetes resource");
                }
            });
            if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new IllegalStateException("Unable to read preview Kubernetes resource: " + name.split("/")[0]);
            }
            return JSON.readTree(output.get(5, TimeUnit.SECONDS));
        } finally {
            process.destroyForcibly();
        }
    }

    private List<String> command(String... args) {
        List<String> command = new ArrayList<>(List.of(System.getenv().getOrDefault("KUBECTL", "kubectl"),
                                                      "--namespace", namespace));
        command.addAll(List.of(args));
        return command;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required environment variable: " + name);
        }
        return value;
    }

    @Override
    public void close() throws Exception {
        try {
            if (database != null) {
                database.close();
            }
        } finally {
            if (portForward != null) {
                portForward.destroyForcibly();
            }
        }
    }
}
