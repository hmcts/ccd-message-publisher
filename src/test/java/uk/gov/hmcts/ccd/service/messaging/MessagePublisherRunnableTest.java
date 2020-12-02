package uk.gov.hmcts.ccd.service.messaging;

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
import uk.gov.hmcts.ccd.data.MessageDTO;
import uk.gov.hmcts.ccd.data.MessageMapper;
import uk.gov.hmcts.ccd.data.MessageQueueCandidateEntity;
import uk.gov.hmcts.ccd.data.MessageQueueCandidateRepository;

import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
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
    @Mock
    private MessageMapper messageMapper;
    @Captor
    private ArgumentCaptor<MessageDTO> messageDtoCaptor;
    @Captor
    private ArgumentCaptor<List<MessageQueueCandidateEntity>> processedEntitiesCaptor;

    private PublishMessageTask publishMessageTask;

    private MessageQueueCandidateEntity messageQueueCandidate1;
    private MessageQueueCandidateEntity messageQueueCandidate2;
    private MessageQueueCandidateEntity messageQueueCandidate3;
    private MessageDTO messageDto1;
    private MessageDTO messageDto2;
    private MessageDTO messageDto3;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);

        publishMessageTask = PublishMessageTask.builder()
            .schedule(SCHEDULE)
            .messageType(MESSAGE_TYPE)
            .destination(DESTINATION)
            .batchSize(BATCH_SIZE).build();

        messageQueueCandidate1 = new MessageQueueCandidateEntity();
        messageQueueCandidate2 = new MessageQueueCandidateEntity();
        messageQueueCandidate3 = new MessageQueueCandidateEntity();
        messageDto1 = new MessageDTO();
        messageDto2 = new MessageDTO();
        messageDto3 = new MessageDTO();

        when(messageMapper.toMessageDto(messageQueueCandidate1)).thenReturn(messageDto1);
        when(messageMapper.toMessageDto(messageQueueCandidate2)).thenReturn(messageDto2);
        when(messageMapper.toMessageDto(messageQueueCandidate3)).thenReturn(messageDto3);

        messagePublisher = new MessagePublisherRunnable(messageQueueCandidateRepository, jmsTemplate,
            publishMessageTask, messageMapper);
    }

    @Test
    void shouldProcessUnpublishedMessagesInSinglePage() {
        List<MessageQueueCandidateEntity> list =
            newArrayList(messageQueueCandidate1, messageQueueCandidate2, messageQueueCandidate3);
        when(messageQueueCandidateRepository.findUnpublishedMessages(eq(MESSAGE_TYPE), any()))
            .thenReturn(new SliceImpl<MessageQueueCandidateEntity>(list));

        messagePublisher.run();

        assertAll(
            () -> verify(jmsTemplate, times(3)).convertAndSend(eq(DESTINATION), messageDtoCaptor.capture()),
            () -> assertThat(messageDtoCaptor.getAllValues().get(0), is(messageDto1)),
            () -> assertThat(messageDtoCaptor.getAllValues().get(1), is(messageDto2)),
            () -> assertThat(messageDtoCaptor.getAllValues().get(2), is(messageDto3)),
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
        List<MessageQueueCandidateEntity> page1List = newArrayList(messageQueueCandidate1, messageQueueCandidate2);
        List<MessageQueueCandidateEntity> page2List = newArrayList(messageQueueCandidate3);
        when(messageQueueCandidateRepository.findUnpublishedMessages(eq(MESSAGE_TYPE), any()))
            .thenReturn(new SliceImpl<MessageQueueCandidateEntity>(page1List, PageRequest.of(0, BATCH_SIZE), true))
            .thenReturn(new SliceImpl<MessageQueueCandidateEntity>(page2List, PageRequest.of(1, BATCH_SIZE), false));

        messagePublisher.run();

        assertAll(
            () -> verify(jmsTemplate, times(3)).convertAndSend(eq(DESTINATION), messageDtoCaptor.capture()),
            () -> assertThat(messageDtoCaptor.getAllValues().get(0), is(messageDto1)),
            () -> assertThat(messageDtoCaptor.getAllValues().get(1), is(messageDto2)),
            () -> assertThat(messageDtoCaptor.getAllValues().get(2), is(messageDto3)),
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
            .thenReturn(new SliceImpl<MessageQueueCandidateEntity>(newArrayList()));

        messagePublisher.run();

        assertAll(
            () -> verifyNoInteractions(jmsTemplate),
            () -> verify(messageQueueCandidateRepository, never()).saveAll(any())
        );
    }

    @Test
    void shouldHaltProcessingOnError() {
        List<MessageQueueCandidateEntity> list =
            newArrayList(messageQueueCandidate1, messageQueueCandidate2, messageQueueCandidate3);
        when(messageQueueCandidateRepository.findUnpublishedMessages(eq(MESSAGE_TYPE), any()))
            .thenReturn(new SliceImpl<MessageQueueCandidateEntity>(list));

        // Throw exception on processing of second message
        doNothing().doThrow(new IllegalStateException(new javax.jms.IllegalStateException("Error")))
            .when(jmsTemplate).convertAndSend(eq(DESTINATION), any(MessageDTO.class));

        messagePublisher.run();

        assertAll(
            () -> verify(jmsTemplate, times(2)).convertAndSend(eq(DESTINATION), messageDtoCaptor.capture()),
            () -> assertThat(messageDtoCaptor.getAllValues().get(0), is(messageDto1)),
            () -> assertThat(messageDtoCaptor.getAllValues().get(1), is(messageDto2)),
            () -> verify(messageQueueCandidateRepository).saveAll(processedEntitiesCaptor.capture()),
            () -> assertThat(processedEntitiesCaptor.getValue().size(), is(1)),
            () -> assertThat(processedEntitiesCaptor.getValue().get(0), is(messageQueueCandidate1)),
            () -> assertThat(processedEntitiesCaptor.getValue().get(0).getPublished(), notNullValue()),
            () -> assertThat(messageQueueCandidate2.getPublished(), nullValue()),
            () -> assertThat(messageQueueCandidate3.getPublished(), nullValue())
        );
    }
}