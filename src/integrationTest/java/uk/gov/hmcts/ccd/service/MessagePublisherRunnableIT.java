package uk.gov.hmcts.ccd.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.ccd.BaseTest;
import uk.gov.hmcts.ccd.data.MessageQueueCandidateEntity;
import uk.gov.hmcts.ccd.data.MessageQueueCandidateRepository;
import uk.gov.hmcts.ccd.config.PublishMessageTask;

import javax.jms.TextMessage;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.collect.Lists.newArrayList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.hmcts.ccd.service.MessageProperties.CASE_ID;
import static uk.gov.hmcts.ccd.service.MessageProperties.CASE_TYPE_ID;
import static uk.gov.hmcts.ccd.service.MessageProperties.EVENT_ID;
import static uk.gov.hmcts.ccd.service.MessageProperties.JURISDICTION_ID;


@Transactional
class MessagePublisherRunnableIT extends BaseTest {

    private static final String INSERT_DATA_SCRIPT = "classpath:sql/insert-message-queue-candidates.sql";
    private static final String INSERT_DATA_SCRIPT_PROPERTIES = "classpath:sql/insert-message-property-candidates.sql";

    private static final String SCHEDULE = "SCHEDULE";
    private static final String MESSAGE_TYPE = "FIRST_MESSAGE_TYPE";
    private static final String DESTINATION = "DESTINATION";
    private static final int BATCH_SIZE = 1000;
    private static final int RETENTION_DAYS = 30;

    private MessagePublisherRunnable messagePublisher;

    @Autowired
    private MessageQueueCandidateRepository messageQueueCandidateRepository;
    @Autowired
    private JmsTemplate jmsTemplate;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);

        PublishMessageTask publishMessageTask = PublishMessageTask.builder()
            .schedule(SCHEDULE)
            .messageType(MESSAGE_TYPE)
            .destination(DESTINATION)
            .batchSize(BATCH_SIZE)
            .publishedRetentionDays(RETENTION_DAYS).build();

        messagePublisher = new MessagePublisherRunnable(messageQueueCandidateRepository, jmsTemplate,
            publishMessageTask);
    }

    @Test
    @Sql(INSERT_DATA_SCRIPT)
    @DirtiesContext
    void shouldProcessUnpublishedMessages() {
        Iterable<MessageQueueCandidateEntity> allMessageQueueCandidates = messageQueueCandidateRepository.findAll();

        messagePublisher.run();

        List<TextMessage> enqueuedMessages = getMessagesFromDestination();
        assertAll(
            () -> assertThat(enqueuedMessages.size(), is(5)),
            () -> assertEnqueuedMessages(enqueuedMessages),
            () -> assertAllPublishedValues(newArrayList(allMessageQueueCandidates))
        );
    }

    @Test
    @Sql(INSERT_DATA_SCRIPT)
    void shouldDeletePublishedMessagesPastRetentionPeriod() {
        List<MessageQueueCandidateEntity> entitiesBefore = newArrayList(messageQueueCandidateRepository.findAll());
        assertThat(entitiesBefore.size(), is(11));
        List<Long> expectedEntityIdsToBeDeleted = entitiesBefore.stream()
            .filter(entity -> entity.getMessageType().equals(MESSAGE_TYPE))
            .filter(entity -> entity.getPublished() != null
                && entity.getPublished().isBefore(LocalDateTime.now().minusDays(RETENTION_DAYS)))
            .map(MessageQueueCandidateEntity::getId)
            .collect(Collectors.toList());

        messagePublisher.run();

        List<MessageQueueCandidateEntity> entitiesAfter = newArrayList(messageQueueCandidateRepository.findAll());
        assertAll(
            () -> assertThat(newArrayList(entitiesAfter).size(), is(9)),
            () -> assertEntitiesNotPresent(entitiesAfter, expectedEntityIdsToBeDeleted)
        );
    }

    private void assertEntitiesNotPresent(List<MessageQueueCandidateEntity> entities,
                                          List<Long> idsToAssertNotPresent) {
        List<Long> entitiesIds = entities.stream()
            .map(MessageQueueCandidateEntity::getId)
            .collect(Collectors.toList());

        idsToAssertNotPresent.forEach(id -> assertFalse(entitiesIds.contains(id)));
    }

    private void assertAllPublishedValues(List<MessageQueueCandidateEntity> allMessageQueueCandidates) {
        List<MessageQueueCandidateEntity> expectedMessageTypeEntitiesOrdered = allMessageQueueCandidates.stream()
            .filter(entity -> entity.getMessageType().equals(MESSAGE_TYPE))
            .sorted((e1, e2) -> e1.getTimeStamp().compareTo(e2.getTimeStamp()))
            .collect(Collectors.toList());

        assertAll(
            () -> assertPublishedInOrder(expectedMessageTypeEntitiesOrdered),
            () -> assertThat(allMessageQueueCandidates.get(0).getPublished(), notNullValue()),
            () -> assertThat(allMessageQueueCandidates.get(1).getPublished(), notNullValue()),
            () -> assertThat(allMessageQueueCandidates.get(2).getPublished(), nullValue()),
            () -> assertThat(allMessageQueueCandidates.get(3).getPublished(), nullValue()),
            () -> assertThat(allMessageQueueCandidates.get(4).getPublished(), notNullValue()),
            () -> assertThat(allMessageQueueCandidates.get(5).getPublished(), notNullValue()),
            () -> assertThat(allMessageQueueCandidates.get(6).getPublished(), notNullValue()),
            () -> assertThat(allMessageQueueCandidates.get(7).getPublished(), notNullValue())
        );
    }

    private void assertPublishedInOrder(List<MessageQueueCandidateEntity> entitiesOrderedByTimestamp) {
        IntStream.range(0, entitiesOrderedByTimestamp.size() - 1).forEach(i ->
            assertTrue(entitiesOrderedByTimestamp.get(i).getPublished()
                .isBefore(entitiesOrderedByTimestamp.get(i + 1).getPublished())));
    }

    @SuppressWarnings("unchecked")
    private List<TextMessage> getMessagesFromDestination() {
        return jmsTemplate
            .browse(DESTINATION, (session, browser) -> Collections.list(browser.getEnumeration()));
    }

    private void assertEnqueuedMessages(List<TextMessage> enqueuedMessages) {
        assertAll(
            () -> assertThat(enqueuedMessages.get(0).getText(), is("{\"key\":\"1\"}")),
            () -> assertThat(enqueuedMessages.get(1).getText(), is("{\"key\":\"2\"}")),
            () -> assertThat(enqueuedMessages.get(2).getText(), is("{\"key\":\"3\"}")),
            () -> assertThat(enqueuedMessages.get(3).getText(), is("{\"key\":\"4\"}")),
            () -> assertThat(enqueuedMessages.get(4).getText(), is("{\"key\":\"5\"}"))
        );
    }

    @Test
    @Sql(INSERT_DATA_SCRIPT_PROPERTIES)
    @DirtiesContext
    void assertPropertiesSet() {
        messagePublisher.run();
        List<TextMessage> output = getMessagesFromDestination();
        assertAll(
            () -> assertThat(output.get(0).getStringProperty(JURISDICTION_ID.getPropertyId()), is("test1")),
            () -> assertThat(output.get(3).getStringProperty(CASE_ID.getPropertyId()), is("test4")),
            () -> assertThat(output.get(4).getStringProperty(CASE_TYPE_ID.getPropertyId()), is("test5")),
            () -> assertFalse(output.get(0).propertyExists(EVENT_ID.getPropertyId())),
            () -> assertFalse(output.get(1).propertyExists(EVENT_ID.getPropertyId())),
            () -> assertFalse(output.get(2).propertyExists("test_property")),
            () -> assertFalse(output.get(3).propertyExists("test_property")),
            () -> assertFalse(output.get(4).propertyExists(CASE_ID.getPropertyId()))
        );
    }
}
