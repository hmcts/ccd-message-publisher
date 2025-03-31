package uk.gov.hmcts.ccd.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.jms.IllegalStateException;
import org.springframework.jms.core.JmsTemplate;
import uk.gov.hmcts.ccd.data.MessageQueueCandidateEntity;
import uk.gov.hmcts.ccd.data.MessageQueueCandidateRepository;
import uk.gov.hmcts.ccd.config.PublishMessageTask;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MessagePublisherRunnableTest {

    private static final String SCHEDULE = "SCHEDULE";
    private static final String MESSAGE_TYPE = "MESSAGE_TYPE";
    private static final String DESTINATION = "DESTINATION";
    private static final int BATCH_SIZE = 1000;

    private MessagePublisherRunnable messagePublisher;

    @Mock
    private MessageQueueCandidateRepository messageQueueCandidateRepository;
    @Mock
    private JmsTemplate jmsTemplate;
    @Captor
    private ArgumentCaptor<JsonNode> messageCaptor;
    @Captor
    private ArgumentCaptor<List<MessageQueueCandidateEntity>> processedEntitiesCaptor;

    private PublishMessageTask publishMessageTask;

    private MessageQueueCandidateEntity messageQueueCandidate1;
    private MessageQueueCandidateEntity messageQueueCandidate2;
    private MessageQueueCandidateEntity messageQueueCandidate3;
    private JsonNode message1;
    private JsonNode message2;
    private JsonNode message3;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        publishMessageTask = PublishMessageTask.builder()
            .schedule(SCHEDULE)
            .messageType(MESSAGE_TYPE)
            .destination(DESTINATION)
            .batchSize(BATCH_SIZE).build();

        message1 = new TextNode("Message1");
        message2 = new TextNode("Message2");
        message3 = new TextNode("Message3");
        messageQueueCandidate1 = new MessageQueueCandidateEntity();
        messageQueueCandidate2 = new MessageQueueCandidateEntity();
        messageQueueCandidate3 = new MessageQueueCandidateEntity();
        messageQueueCandidate1.setMessageInformation(message1);
        messageQueueCandidate2.setMessageInformation(message2);
        messageQueueCandidate3.setMessageInformation(message3);

        messagePublisher = new MessagePublisherRunnable(messageQueueCandidateRepository, jmsTemplate,
            publishMessageTask);
    }

    @Test
    void shouldProcessUnpublishedMessagesInSinglePage() {
        List<MessageQueueCandidateEntity> list =
            List.of(messageQueueCandidate1, messageQueueCandidate2, messageQueueCandidate3);
        when(messageQueueCandidateRepository.findUnpublishedMessages(eq(MESSAGE_TYPE), any()))
            .thenReturn(new SliceImpl<MessageQueueCandidateEntity>(list));

        messagePublisher.run();

        assertAll(
            () -> verify(jmsTemplate, times(3)).convertAndSend(eq(DESTINATION), messageCaptor.capture(), any()),
            () -> assertThat(messageCaptor.getAllValues().get(0), is(message1)),
            () -> assertThat(messageCaptor.getAllValues().get(1), is(message2)),
            () -> assertThat(messageCaptor.getAllValues().get(2), is(message3)),
            () -> verify(messageQueueCandidateRepository).saveAll(processedEntitiesCaptor.capture()),
            () -> assertThat(processedEntitiesCaptor.getValue().size(), is(3)),
            () -> assertThat(processedEntitiesCaptor.getValue().get(0), is(messageQueueCandidate1)),
            () -> assertThat(processedEntitiesCaptor.getValue().get(0).getPublished(), notNullValue()),
            () -> assertThat(processedEntitiesCaptor.getValue().get(1), is(messageQueueCandidate2)),
            () -> assertThat(processedEntitiesCaptor.getValue().get(1).getPublished(), notNullValue()),
            () -> assertThat(processedEntitiesCaptor.getValue().get(2), is(messageQueueCandidate3)),
            () -> assertThat(processedEntitiesCaptor.getValue().get(2).getPublished(), notNullValue())
        );
    }

    @Test
    void shouldProcessUnpublishedMessagesOverMultiplePages() {
        List<MessageQueueCandidateEntity> page1List = List.of(messageQueueCandidate1, messageQueueCandidate2);
        List<MessageQueueCandidateEntity> page2List = List.of(messageQueueCandidate3);
        when(messageQueueCandidateRepository.findUnpublishedMessages(eq(MESSAGE_TYPE), any()))
            .thenReturn(new SliceImpl<MessageQueueCandidateEntity>(page1List, PageRequest.of(0, BATCH_SIZE), true))
            .thenReturn(new SliceImpl<MessageQueueCandidateEntity>(page2List, PageRequest.of(1, BATCH_SIZE), false));

        messagePublisher.run();

        assertAll(
            () -> verify(jmsTemplate, times(3)).convertAndSend(eq(DESTINATION), messageCaptor.capture(), any()),
            () -> assertThat(messageCaptor.getAllValues().get(0), is(message1)),
            () -> assertThat(messageCaptor.getAllValues().get(1), is(message2)),
            () -> assertThat(messageCaptor.getAllValues().get(2), is(message3)),
            () -> verify(messageQueueCandidateRepository).saveAll(processedEntitiesCaptor.capture()),
            () -> assertThat(processedEntitiesCaptor.getValue().size(), is(3)),
            () -> assertThat(processedEntitiesCaptor.getValue().get(0), is(messageQueueCandidate1)),
            () -> assertThat(processedEntitiesCaptor.getValue().get(0).getPublished(), notNullValue()),
            () -> assertThat(processedEntitiesCaptor.getValue().get(1), is(messageQueueCandidate2)),
            () -> assertThat(processedEntitiesCaptor.getValue().get(1).getPublished(), notNullValue()),
            () -> assertThat(processedEntitiesCaptor.getValue().get(2), is(messageQueueCandidate3)),
            () -> assertThat(processedEntitiesCaptor.getValue().get(2).getPublished(), notNullValue())
        );
    }

    @Test
    void shouldDoNothingWhenNoResultsFound() {
        when(messageQueueCandidateRepository.findUnpublishedMessages(eq(MESSAGE_TYPE), any()))
            .thenReturn(new SliceImpl<MessageQueueCandidateEntity>(List.of()));

        messagePublisher.run();

        assertAll(
            () -> verifyNoInteractions(jmsTemplate),
            () -> verify(messageQueueCandidateRepository, never()).saveAll(any())
        );
    }

    @Test
    void shouldHaltProcessingOnError() {
        List<MessageQueueCandidateEntity> list =
            List.of(messageQueueCandidate1, messageQueueCandidate2, messageQueueCandidate3);
        when(messageQueueCandidateRepository.findUnpublishedMessages(eq(MESSAGE_TYPE), any()))
            .thenReturn(new SliceImpl<MessageQueueCandidateEntity>(list));

        // Throw exception on processing of second message
        doNothing().doThrow(new IllegalStateException(new jakarta.jms.IllegalStateException("Error")))
            .when(jmsTemplate).convertAndSend(eq(DESTINATION), any(JsonNode.class), any());

        messagePublisher.run();

        assertAll(
            () -> verify(jmsTemplate, times(2)).convertAndSend(eq(DESTINATION), messageCaptor.capture(), any()),
            () -> assertThat(messageCaptor.getAllValues().get(0), is(message1)),
            () -> assertThat(messageCaptor.getAllValues().get(1), is(message2)),
            () -> verify(messageQueueCandidateRepository).saveAll(processedEntitiesCaptor.capture()),
            () -> assertThat(processedEntitiesCaptor.getValue().size(), is(1)),
            () -> assertThat(processedEntitiesCaptor.getValue().get(0), is(messageQueueCandidate1)),
            () -> assertThat(processedEntitiesCaptor.getValue().get(0).getPublished(), notNullValue()),
            () -> assertThat(messageQueueCandidate2.getPublished(), nullValue()),
            () -> assertThat(messageQueueCandidate3.getPublished(), nullValue())
        );
    }
}
