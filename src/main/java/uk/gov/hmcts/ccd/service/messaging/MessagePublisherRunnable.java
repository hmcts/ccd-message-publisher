package uk.gov.hmcts.ccd.service.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.jms.core.JmsTemplate;
import uk.gov.hmcts.ccd.data.MessageMapper;
import uk.gov.hmcts.ccd.data.MessageQueueCandidateEntity;
import uk.gov.hmcts.ccd.data.MessageQueueCandidateRepository;

import java.time.LocalDateTime;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

@Slf4j
public class MessagePublisherRunnable implements Runnable {

    private MessageQueueCandidateRepository messageQueueCandidateRepository;
    private JmsTemplate jmsTemplate;
    private PublishMessageTask publishMessageTask;
    private MessageMapper messageMapper;

    public MessagePublisherRunnable(MessageQueueCandidateRepository messageQueueCandidateRepository,
                                    JmsTemplate jmsTemplate,
                                    PublishMessageTask publishMessageTask,
                                    MessageMapper messageMapper) {
        this.messageQueueCandidateRepository = messageQueueCandidateRepository;
        this.jmsTemplate = jmsTemplate;
        this.publishMessageTask = publishMessageTask;
        this.messageMapper = messageMapper;
    }

    @Override
    public void run() {
        processUnpublishedMessages(PageRequest.of(0, publishMessageTask.getBatchSize()), newArrayList());
        deletePublishedMessages();
    }

    private void processUnpublishedMessages(Pageable pageable,
                                            List<MessageQueueCandidateEntity> processedEntities) {
        Slice<MessageQueueCandidateEntity> unpublishedMessagesPaginated = null;
        boolean hasError = false;

        try {
            unpublishedMessagesPaginated = messageQueueCandidateRepository
                .findUnpublishedMessages(publishMessageTask.getMessageType(), pageable);
            publishMessages(unpublishedMessagesPaginated, processedEntities);
        } catch (Exception e) {
            log.error(String.format("Error encountered during processing of unpublished messages for "
                + "message type '%s'", publishMessageTask.getMessageType()), e);
            hasError = true;
        }

        if (!hasError && unpublishedMessagesPaginated.hasNext()) {
            processUnpublishedMessages(unpublishedMessagesPaginated.nextPageable(), processedEntities);
        } else if (!processedEntities.isEmpty()) {
            messageQueueCandidateRepository.saveAll(processedEntities);
            log.info("[Message Type '%s'] Published %s messages to destination %s",
                publishMessageTask.getMessageType(), processedEntities.size(), publishMessageTask.getDestination());
        }
    }

    private void publishMessages(Slice<MessageQueueCandidateEntity> messagesToPublish,
                                 List<MessageQueueCandidateEntity> processedEntities) {
        messagesToPublish.get().forEach(entity -> {
            jmsTemplate.convertAndSend(publishMessageTask.getDestination(), messageMapper.toMessageDto(entity));
            entity.setPublished(LocalDateTime.now());
            processedEntities.add(entity);
        });
    }

    private void deletePublishedMessages() {
        LocalDateTime retentionDate = LocalDateTime.now().minusDays(publishMessageTask.getPublishedRetentionDays());
        int result = messageQueueCandidateRepository
            .deletePublishedMessages(retentionDate, publishMessageTask.getMessageType());
        log.debug(String.format("[Message Type '%s'] Deleted %s records with publish date before %s",
            publishMessageTask.getMessageType(), result, retentionDate.toString()));
    }
}
