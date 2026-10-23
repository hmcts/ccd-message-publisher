package uk.gov.hmcts.ccd.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.BytesMessage;
import jakarta.jms.Message;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.ccd.MessagePublisherApplication;
import uk.gov.hmcts.ccd.config.MessagePublisherParams;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(classes = MessagePublisherApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"CCD_CASE_EVENTS_ENABLED=true", "CCD_CASE_EVENTS_DESTINATION=scheduled-publishing-test",
        "CCD_CASE_EVENTS_SCHEDULE=*/1 * * * * *",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.datasource.url=jdbc:tc:postgresql:9.6:///databasename?TC_INITSCRIPT=sql/init-db.sql"})
@ActiveProfiles("dev")
@DirtiesContext
class ScheduledPublishingIT {

    @Autowired
    private JdbcTemplate database;

    @MockitoSpyBean
    private JmsTemplate jmsTemplate;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessagePublisherParams publisherParams;

    @Test
    @DirtiesContext
    void shouldResumePublishingOnScheduledExecutionAfterBrokerRecovery() throws Exception {
        AtomicBoolean unavailable = new AtomicBoolean(true);
        AtomicInteger failures = new AtomicInteger();
        doAnswer(invocation -> {
            if (unavailable.get()) {
                failures.incrementAndGet();
                throw new org.springframework.jms.IllegalStateException(
                    new jakarta.jms.IllegalStateException("Simulated broker outage"));
            }
            return invocation.callRealMethod();
        }).when(jmsTemplate).convertAndSend(eq("scheduled-publishing-test"),
            any(JsonNode.class), any(MessagePostProcessor.class));

        String marker = UUID.randomUUID().toString();
        JsonNode first = objectMapper.createObjectNode().put("CaseId", marker).put("sequence", 1);
        JsonNode second = objectMapper.createObjectNode().put("CaseId", marker).put("sequence", 2);
        Long firstId = insertCandidate(first, LocalDateTime.now(Clock.systemUTC()).minusSeconds(2));
        Long secondId = insertCandidate(second, LocalDateTime.now(Clock.systemUTC()).minusSeconds(1));
        try {
            await().atMost(Duration.ofSeconds(20)).until(() -> failures.get() > 0);
            for (Long id : List.of(firstId, secondId)) {
                assertThat(database.queryForObject(
                    "select published from message_queue_candidates where id = ?", Timestamp.class, id)).isNull();
            }

            unavailable.set(false);
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                for (Long id : List.of(firstId, secondId)) {
                    assertThat(database.queryForObject(
                        "select published from message_queue_candidates where id = ?", Timestamp.class, id))
                        .isNotNull();
                }
            });

            JmsTemplate consumer = new JmsTemplate(jmsTemplate.getConnectionFactory());
            consumer.setReceiveTimeout(5000);
            for (JsonNode expected : List.of(first, second)) {
                Message received = consumer.receive("scheduled-publishing-test");
                assertThat(received).isInstanceOf(BytesMessage.class);
                BytesMessage bytesMessage = (BytesMessage) received;
                byte[] bytes = new byte[(int) bytesMessage.getBodyLength()];
                bytesMessage.readBytes(bytes);
                assertThat(objectMapper.readTree(new String(bytes, StandardCharsets.UTF_8))).isEqualTo(expected);
            }
            Boolean extraMessages = consumer.browse("scheduled-publishing-test",
                (session, browser) -> browser.getEnumeration().hasMoreElements());
            assertThat(extraMessages).isFalse();
        } finally {
            unavailable.set(false);
            database.update("delete from message_queue_candidates where id in (?, ?)", firstId, secondId);
        }
    }

    private Long insertCandidate(JsonNode body, LocalDateTime timestamp) {
        return database.queryForObject(
            "insert into message_queue_candidates (message_type, time_stamp, message_information) "
                + "values (?, ?, ?::jsonb) returning id",
            Long.class, "CASE_EVENT", Timestamp.valueOf(timestamp), body.toString());
    }

    @Test
    void shouldPublishThroughTheSchedulerAndServeHealthyProbesOverHttp() throws Exception {
        String marker = UUID.randomUUID().toString();
        assertThat(publisherParams.getEnabledTasks()).singleElement().satisfies(task -> {
            assertThat(task.getMessageType()).isEqualTo("CASE_EVENT");
            assertThat(task.getDestination()).isEqualTo("scheduled-publishing-test");
        });
        JsonNode body = objectMapper.createObjectNode().put("CaseId", marker).put("CaseTypeId", "SCHEDULING_TEST");
        Long candidateId = database.queryForObject(
            "insert into message_queue_candidates (message_type, time_stamp, message_information) "
                + "values (?, ?, ?::jsonb) returning id",
            Long.class, "CASE_EVENT", Timestamp.valueOf(LocalDateTime.now(Clock.systemUTC())), body.toString());

        try {
            // Observe a real scheduler tick; never call MessagePublisherRunnable directly.
            await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100)).untilAsserted(() ->
                assertThat(database.queryForObject(
                    "select published from message_queue_candidates where id = ?", Timestamp.class, candidateId))
                    .isNotNull());

            JmsTemplate consumer = new JmsTemplate(jmsTemplate.getConnectionFactory());
            consumer.setReceiveTimeout(5000);
            Message received = consumer.receive("scheduled-publishing-test");
            assertThat(received).isInstanceOf(BytesMessage.class);
            BytesMessage bytesMessage = (BytesMessage) received;
            byte[] bytes = new byte[(int) bytesMessage.getBodyLength()];
            bytesMessage.readBytes(bytes);
            assertThat(objectMapper.readTree(new String(bytes, StandardCharsets.UTF_8))).isEqualTo(body);
            assertThat(received.getStringProperty("case_id")).isEqualTo(marker);
            assertThat(received.getStringProperty("JMSXGroupID")).isEqualTo(marker);

            for (String group : new String[]{"liveness", "readiness"}) {
                ResponseEntity<JsonNode> response = http.getForEntity("/health/" + group, JsonNode.class);
                assertThat(response.getStatusCode().value()).as("HTTP %s probe", group).isEqualTo(200);
                assertThat(response.getBody().path("status").asText()).isEqualTo("UP");
            }
        } finally {
            database.update("delete from message_queue_candidates where id = ?", candidateId);
        }
    }
}
