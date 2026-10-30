package uk.gov.hmcts.ccd.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.jms.BytesMessage;
import jakarta.jms.Message;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jms.IllegalStateException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessagePostProcessor;
import org.springframework.jms.support.converter.MessageConversionException;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import uk.gov.hmcts.ccd.BaseTest;
import uk.gov.hmcts.ccd.config.PublishMessageTask;
import uk.gov.hmcts.ccd.data.MessageQueueCandidateEntity;
import uk.gov.hmcts.ccd.data.MessageQueueCandidateRepository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql("classpath:sql/cleanup-message-queue-candidates.sql")
@Sql(scripts = "classpath:sql/cleanup-message-queue-candidates.sql",
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class MessagePublisherRecoveryIT extends BaseTest {

    private static final String MESSAGE_TYPE = "RECOVERY_TEST";
    private static final LocalDateTime FIXTURE_TIME = LocalDateTime.of(2024, 1, 1, 12, 0);

    @Autowired
    private MessageQueueCandidateRepository repository;

    @Autowired
    private JmsTemplate jmsTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    private String destination;
    private List<Long> candidateIds;
    private List<String> expectedBodies;

    @BeforeEach
    void setUp() {
        // Repository calls must commit independently, as they do in a scheduled publisher run.
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        destination = "publisher-recovery-" + UUID.randomUUID();
        candidateIds = new ArrayList<>();
        expectedBodies = new ArrayList<>();
    }

    @ParameterizedTest
    @CsvSource({"1, 7", "2, 6", "2, 7", "3, 6", "3, 7"})
    void shouldCommitEveryBatchInTimestampOrderAndNotRepublishOnNextRun(int batchSize, int count) throws Exception {
        seedCandidates(count, false);
        final Long unrelatedId = saveCandidate("OTHER_TYPE", "unrelated", FIXTURE_TIME, false).getId();
        final MessageQueueCandidateEntity alreadyPublished =
            saveCandidate(MESSAGE_TYPE, "already-published", LocalDateTime.now(), true);

        publisher(repository, jmsTemplate, batchSize).run();

        assertThat(messageBodies()).containsExactlyElementsOf(expectedBodies);
        assertPublishedMarkers(candidateIds);
        assertUnpublished(List.of(unrelatedId));
        assertThat(publishedAt(alreadyPublished.getId())).isEqualTo(alreadyPublished.getPublished());

        // A new runnable must observe the committed markers and send nothing further.
        publisher(repository, jmsTemplate, batchSize).run();

        assertThat(messageBodies()).containsExactlyElementsOf(expectedBodies);
        assertPublishedMarkers(candidateIds);
        assertUnpublished(List.of(unrelatedId));
    }

    @Test
    void shouldPublishEveryCandidateAcrossPagesWhenTimestampsAreIdentical() throws Exception {
        seedCandidates(7, true);

        publisher(repository, jmsTemplate, 2).run();

        assertThat(messageBodies()).containsExactlyInAnyOrderElementsOf(expectedBodies);
        assertPublishedMarkers(candidateIds);

        publisher(repository, jmsTemplate, 2).run();

        assertThat(messageBodies()).containsExactlyInAnyOrderElementsOf(expectedBodies);
        assertPublishedMarkers(candidateIds);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 7})
    void shouldCommitSuccessfulSendsAndResumeAfterBrokerFailure(int failingSend) throws Exception {
        seedCandidates(7, false);
        JmsTemplate failingBroker = brokerFailingOnSend(failingSend, false);

        publisher(repository, failingBroker, 2).run();

        assertThat(messageBodies()).containsExactlyElementsOf(expectedBodies.subList(0, failingSend - 1));
        assertPublishedMarkers(candidateIds.subList(0, failingSend - 1));
        assertUnpublished(candidateIds.subList(failingSend - 1, candidateIds.size()));

        publisher(repository, jmsTemplate, 2).run();

        assertThat(messageBodies()).containsExactlyElementsOf(expectedBodies);
        assertPublishedMarkers(candidateIds);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4})
    void shouldCommitEarlierPagesAndResumeAfterDatabaseReadFailure(int failingRead) throws Exception {
        seedCandidates(7, false);
        MessageQueueCandidateRepository failingRepository =
            mock(MessageQueueCandidateRepository.class, delegatesTo(repository));
        AtomicInteger reads = new AtomicInteger();
        doAnswer(invocation -> {
            if (reads.incrementAndGet() == failingRead) {
                throw new DataAccessResourceFailureException("Database unavailable while reading candidates");
            }
            return repository.findUnpublishedMessages(invocation.getArgument(0), invocation.getArgument(1));
        }).when(failingRepository).findUnpublishedMessages(eq(MESSAGE_TYPE), any(Pageable.class));

        publisher(failingRepository, jmsTemplate, 2).run();

        int successfulSends = (failingRead - 1) * 2;
        assertThat(messageBodies()).containsExactlyElementsOf(expectedBodies.subList(0, successfulSends));
        assertPublishedMarkers(candidateIds.subList(0, successfulSends));
        assertUnpublished(candidateIds.subList(successfulSends, candidateIds.size()));

        publisher(repository, jmsTemplate, 2).run();

        assertThat(messageBodies()).containsExactlyElementsOf(expectedBodies);
        assertPublishedMarkers(candidateIds);
    }

    @Test
    void shouldRedeliverWithoutLossAfterPublishedMarkersRollBack() throws Exception {
        seedCandidates(7, false);
        MessageQueueCandidateRepository failingRepository =
            mock(MessageQueueCandidateRepository.class, delegatesTo(repository));
        doAnswer(invocation -> {
            new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                repository.saveAll(invocation.<List<MessageQueueCandidateEntity>>getArgument(0));
                entityManager.flush();
                assertPublishedMarkers(candidateIds);
                throw new DataAccessResourceFailureException("Database failure before commit");
            });
            return null;
        }).when(failingRepository).saveAll(any());

        assertThatThrownBy(() -> publisher(failingRepository, jmsTemplate, 2).run())
            .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(messageBodies()).containsExactlyElementsOf(expectedBodies);
        assertUnpublished(candidateIds);

        publisher(repository, jmsTemplate, 2).run();

        // Broker sends and database commits are separate: recovery provides at-least-once delivery.
        List<String> redeliveredBodies = new ArrayList<>(expectedBodies);
        redeliveredBodies.addAll(expectedBodies);
        assertThat(messageBodies()).containsExactlyElementsOf(redeliveredBodies);
        assertPublishedMarkers(candidateIds);

        publisher(repository, jmsTemplate, 2).run();
        assertThat(messageBodies()).containsExactlyElementsOf(redeliveredBodies);
    }

    @Test
    void shouldRetryAnUnacknowledgedSendWithoutLosingLaterMessages() throws Exception {
        seedCandidates(7, false);

        publisher(repository, brokerFailingOnSend(2, true), 2).run();

        assertThat(messageBodies()).containsExactlyElementsOf(expectedBodies.subList(0, 2));
        assertPublishedMarkers(candidateIds.subList(0, 1));
        assertUnpublished(candidateIds.subList(1, candidateIds.size()));

        publisher(repository, jmsTemplate, 2).run();

        List<String> recoveredBodies = new ArrayList<>(expectedBodies.subList(0, 2));
        recoveredBodies.addAll(expectedBodies.subList(1, expectedBodies.size()));
        assertThat(messageBodies()).containsExactlyElementsOf(recoveredBodies);
        assertPublishedMarkers(candidateIds);
    }

    @Test
    void shouldRetainRejectedCandidateAndLaterMessagesUntilPayloadIsCorrected() throws Exception {
        seedCandidates(3, false);
        JsonNode rejectedPayload = objectMapper.readTree(expectedBodies.get(1));
        MessageConverter converter = mock(MessageConverter.class, delegatesTo(jmsTemplate.getMessageConverter()));
        doThrow(new MessageConversionException("Simulated payload conversion rejection"))
            .when(converter).toMessage(eq(rejectedPayload), any(jakarta.jms.Session.class));
        JmsTemplate rejectingBroker = new JmsTemplate(jmsTemplate.getConnectionFactory());
        rejectingBroker.setMessageConverter(converter);

        publisher(repository, rejectingBroker, 2).run();

        assertThat(messageBodies()).containsExactly(expectedBodies.get(0));
        assertPublishedMarkers(candidateIds.subList(0, 1));
        assertUnpublished(candidateIds.subList(1, 3));

        // A repeated attempt still cannot pass the rejected candidate, but must not duplicate earlier sends.
        publisher(repository, rejectingBroker, 2).run();
        assertThat(messageBodies()).containsExactly(expectedBodies.get(0));
        assertUnpublished(candidateIds.subList(1, 3));

        JsonNode correctedPayload = objectMapper.createObjectNode().put("key", "corrected-message-2");
        jdbcTemplate.update("update message_queue_candidates set message_information = ?::jsonb where id = ?",
            correctedPayload.toString(), candidateIds.get(1));
        publisher(repository, rejectingBroker, 2).run();

        assertThat(messageBodies()).containsExactly(
            expectedBodies.get(0), correctedPayload.toString(), expectedBodies.get(2));
        assertPublishedMarkers(candidateIds);
    }

    private MessagePublisherRunnable publisher(MessageQueueCandidateRepository candidateRepository,
                                               JmsTemplate broker, int batchSize) {
        PublishMessageTask task = PublishMessageTask.builder()
            .messageType(MESSAGE_TYPE)
            .destination(destination)
            .batchSize(batchSize)
            .publishedRetentionDays(30)
            .build();
        return new MessagePublisherRunnable(candidateRepository, broker, task);
    }

    private JmsTemplate brokerFailingOnSend(int failingSend, boolean sendBeforeFailure) {
        JmsTemplate failingBroker = mock(JmsTemplate.class, delegatesTo(jmsTemplate));
        AtomicInteger sends = new AtomicInteger();
        doAnswer(invocation -> {
            boolean fail = sends.incrementAndGet() == failingSend;
            if (!fail || sendBeforeFailure) {
                jmsTemplate.convertAndSend(invocation.<String>getArgument(0), invocation.getArgument(1),
                                           invocation.<MessagePostProcessor>getArgument(2));
            }
            if (fail) {
                throw new IllegalStateException(
                    new jakarta.jms.IllegalStateException("Simulated broker connection failure"));
            }
            return null;
        }).when(failingBroker).convertAndSend(anyString(), any(JsonNode.class), any(MessagePostProcessor.class));
        return failingBroker;
    }

    private void seedCandidates(int count, boolean identicalTimestamps) {
        // Insert in reverse timestamp order so insertion order cannot satisfy the ordering assertion.
        for (int index = count; index > 0; index--) {
            LocalDateTime timestamp = identicalTimestamps ? FIXTURE_TIME : FIXTURE_TIME.plusSeconds(index);
            MessageQueueCandidateEntity candidate = saveCandidate(MESSAGE_TYPE, "message-" + index, timestamp, false);
            candidateIds.add(0, candidate.getId());
            expectedBodies.add(0, candidate.getMessageInformation().toString());
        }
    }

    private MessageQueueCandidateEntity saveCandidate(String type, String key, LocalDateTime timestamp,
                                                      boolean published) {
        MessageQueueCandidateEntity candidate = new MessageQueueCandidateEntity();
        candidate.setMessageType(type);
        candidate.setTimeStamp(timestamp);
        candidate.setMessageInformation(objectMapper.createObjectNode().put("key", key));
        if (published) {
            candidate.setPublished(LocalDateTime.now().withNano(0));
        }
        repository.saveAll(List.of(candidate));
        return candidate;
    }

    private void assertPublishedMarkers(List<Long> ids) {
        // Direct SQL observes stored values rather than fields on managed or previously loaded entities.
        for (Long id : ids) {
            assertThat(publishedAt(id)).as("Published marker for candidate %s", id).isNotNull();
        }
    }

    private void assertUnpublished(List<Long> ids) {
        for (Long id : ids) {
            assertThat(publishedAt(id)).as("Unpublished candidate %s", id).isNull();
        }
    }

    private LocalDateTime publishedAt(Long id) {
        return jdbcTemplate.queryForObject("select published from message_queue_candidates where id = ?",
                                           LocalDateTime.class, id);
    }

    @SuppressWarnings("unchecked")
    private List<String> messageBodies() throws Exception {
        List<Message> messages = jmsTemplate.browse(destination,
            (session, browser) -> Collections.list(browser.getEnumeration()));
        List<String> bodies = new ArrayList<>();
        for (Message message : messages) {
            assertThat(message).isInstanceOf(BytesMessage.class);
            BytesMessage bytesMessage = (BytesMessage) message;
            byte[] bytes = new byte[(int) bytesMessage.getBodyLength()];
            bytesMessage.readBytes(bytes);
            bodies.add(new String(bytes, StandardCharsets.UTF_8));
        }
        return bodies;
    }
}
