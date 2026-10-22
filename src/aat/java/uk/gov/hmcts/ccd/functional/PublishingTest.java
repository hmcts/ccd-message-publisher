package uk.gov.hmcts.ccd.functional;

import com.azure.core.amqp.AmqpRetryOptions;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClientBuilder;
import com.azure.messaging.servicebus.administration.models.CreateRuleOptions;
import com.azure.messaging.servicebus.administration.models.CreateSubscriptionOptions;
import com.azure.messaging.servicebus.administration.models.SqlRuleFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Black-box checks: only the deployed application may publish or mark the seeded rows. */
@EnabledIfEnvironmentVariable(named = "TEST_URL",
    matches = "https://ccd-message-publisher-[a-z0-9-]+\\.preview\\.platform\\.hmcts\\.net/?")
@Timeout(120)
class PublishingTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static PreviewEnvironment preview;
    private static ServiceBusAdministrationClient administration;
    private ServiceBusReceiverClient receiver;
    private String subscription;
    private String caseType;

    @BeforeAll
    @Timeout(60)
    static void connectToPreview() throws Exception {
        preview = PreviewEnvironment.connect();
        administration = new ServiceBusAdministrationClientBuilder()
            .connectionString(preview.brokerConnectionString()).buildClient();
        requireConfiguredTopic(administration, preview.topic());
    }

    static void requireConfiguredTopic(ServiceBusAdministrationClient client, String topic) {
        boolean exists = client.listTopics().stream().anyMatch(properties -> topic.equals(properties.getName()));
        if (!exists) {
            throw new IllegalStateException("Configured publisher topic '" + topic
                + "' was not found in the preview Service Bus namespace. Check the selected publisher's "
                + "CCD_CASE_EVENTS_DESTINATION and SERVICE_BUS_CONNECTION_STRING; no candidates were inserted.");
        }
    }

    @BeforeEach
    @Timeout(60)
    void createFilteredSubscription() {
        String marker = UUID.randomUUID().toString();
        String requestedSubscription = "ccd-8294-" + marker;
        caseType = "FUNCTIONAL_" + marker;
        // This subscription receives only this scenario's messages, never another consumer's traffic.
        administration.createSubscription(preview.topic(), requestedSubscription, "scenario",
            new CreateSubscriptionOptions().setAutoDeleteOnIdle(Duration.ofMinutes(5)),
            new CreateRuleOptions(new SqlRuleFilter("case_type_id = '" + caseType + "'")));
        subscription = requestedSubscription;
        receiver = new ServiceBusClientBuilder().connectionString(preview.brokerConnectionString())
            .retryOptions(new AmqpRetryOptions().setMaxRetries(1).setTryTimeout(Duration.ofSeconds(15)))
            .receiver().topicName(preview.topic()).subscriptionName(subscription).buildClient();
    }

    @Test
    void publishesScheduledCandidateWithItsPayloadRoutingPropertiesAndDatabaseMarker() throws Exception {
        String caseId = UUID.randomUUID().toString();
        JsonNode body = body(caseId, "created");
        final long candidate = insert(body, Instant.now(), null);

        ServiceBusReceivedMessage message = receive(1).getFirst();
        assertThat(JSON.readTree(message.getBody().toString())).isEqualTo(body);
        assertThat(message.getApplicationProperties()).containsEntry("case_id", caseId)
            .containsEntry("case_type_id", caseType).containsEntry("jurisdiction_id", "AUTOTEST")
            .containsEntry("event_id", "created");
        assertThat(message.getSessionId()).isEqualTo(caseId);
        awaitPublished(candidate);
    }

    @Test
    void deliversCandidatesInTimestampThenIdOrder() throws Exception {
        String caseId = UUID.randomUUID().toString();
        Instant timestamp = Instant.now().minusSeconds(5);
        List<Long> ids = new ArrayList<>();
        preview.database().setAutoCommit(false);
        try {
            ids.add(insert(body(caseId, "third"), timestamp.plusSeconds(1), null));
            ids.add(insert(body(caseId, "first"), timestamp, null));
            ids.add(insert(body(caseId, "second"), timestamp, null));
            preview.database().commit();
        } finally {
            preview.database().rollback();
            preview.database().setAutoCommit(true);
        }

        List<String> events = new ArrayList<>();
        for (ServiceBusReceivedMessage message : receive(3)) {
            events.add(JSON.readTree(message.getBody().toString()).path("EventId").asText());
            assertThat(message.getSessionId()).isEqualTo(caseId);
        }
        assertThat(events).containsExactly("first", "second", "third");
        for (long id : ids) {
            awaitPublished(id);
        }
    }

    @Test
    void leavesAlreadyPublishedCandidatesUnchangedWhilePublishingPendingOnes() throws Exception {
        String caseId = UUID.randomUUID().toString();
        Instant timestamp = Instant.now().minusSeconds(5);
        // PostgreSQL timestamps have microsecond precision.
        Timestamp published = Timestamp.from(timestamp.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        final long existing = insert(body(caseId, "already-published"), timestamp, published);
        long pending = insert(body(caseId, "pending"), timestamp, null);

        ServiceBusReceivedMessage message = receive(1).getFirst();
        assertThat(JSON.readTree(message.getBody().toString()).path("EventId").asText()).isEqualTo("pending");
        awaitPublished(pending);
        // Observe more than two default ten-second scheduler intervals after a successful tick.
        assertThat(receiver.receiveMessages(1, Duration.ofSeconds(25)).stream().toList()).isEmpty();
        assertThat(published(existing)).isEqualTo(published);
    }

    private JsonNode body(String caseId, String event) {
        return JSON.createObjectNode().put("CaseId", caseId).put("CaseTypeId", caseType)
            .put("JurisdictionId", "AUTOTEST").put("EventId", event)
            .set("Data", JSON.createObjectNode().put("description", "preview publishing functional test"));
    }

    private long insert(JsonNode body, Instant timestamp, Timestamp published) throws Exception {
        try (PreparedStatement statement = preview.database().prepareStatement(
            "insert into message_queue_candidates (message_type, time_stamp, message_information, published) "
                + "values ('CASE_EVENT', ?, ?::jsonb, ?) returning id")) {
            statement.setTimestamp(1, Timestamp.from(timestamp));
            statement.setString(2, body.toString());
            statement.setTimestamp(3, published);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getLong(1);
            }
        }
    }

    private List<ServiceBusReceivedMessage> receive(int count) {
        List<ServiceBusReceivedMessage> messages = new ArrayList<>();
        Instant deadline = Instant.now().plusSeconds(60);
        while (messages.size() < count && Instant.now().isBefore(deadline)) {
            for (ServiceBusReceivedMessage message : receiver.receiveMessages(
                count - messages.size(), Duration.ofSeconds(5))) {
                messages.add(message);
                receiver.complete(message);
            }
        }
        assertThat(messages).as("messages emitted by the deployed publisher within 60 seconds").hasSize(count);
        return messages;
    }

    private void awaitPublished(long candidate) {
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> assertThat(published(candidate))
                .as("candidate %s published marker", candidate).isNotNull());
    }

    private Timestamp published(long candidate) throws Exception {
        try (PreparedStatement statement = preview.database().prepareStatement(
            "select published from message_queue_candidates where id = ?")) {
            statement.setLong(1, candidate);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getTimestamp(1);
            }
        }
    }

    @AfterEach
    @Timeout(60)
    void cleanUpScenario() throws Exception {
        try {
            if (receiver != null) {
                receiver.close();
            }
        } finally {
            try {
                if (caseType != null) {
                    try (PreparedStatement statement = preview.database().prepareStatement(
                        "delete from message_queue_candidates where message_information->>'CaseTypeId' = ?")) {
                        statement.setString(1, caseType);
                        statement.executeUpdate();
                    }
                }
            } finally {
                if (subscription != null) {
                    administration.deleteSubscription(preview.topic(), subscription);
                }
            }
        }
    }

    @AfterAll
    @Timeout(30)
    static void disconnect() throws Exception {
        if (preview != null) {
            preview.close();
        }
    }
}
