package uk.gov.hmcts.ccd.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.jms.core.JmsTemplate;
import uk.gov.hmcts.ccd.config.PublishMessageTask;
import uk.gov.hmcts.ccd.data.MessageQueueCandidateEntity;
import uk.gov.hmcts.ccd.data.MessageQueueCandidateRepository;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class MessagePublisherRunnable implements Runnable {

    private MessageQueueCandidateRepository messageQueueCandidateRepository;
    private JmsTemplate jmsTemplate;
    private PublishMessageTask publishMessageTask;
    private String logPrefix;

    public MessagePublisherRunnable(MessageQueueCandidateRepository messageQueueCandidateRepository,
                                    JmsTemplate jmsTemplate,
                                    PublishMessageTask publishMessageTask) {
        this.messageQueueCandidateRepository = messageQueueCandidateRepository;
        this.jmsTemplate = jmsTemplate;
        this.publishMessageTask = publishMessageTask;
        this.logPrefix = String.format("[Message Type - %s]", publishMessageTask.getMessageType());
    }

    @Override
    public void run() {
        log.debug(String.format("%s Starting publish message task", logPrefix));
        processUnpublishedMessages(
            PageRequest.of(0, publishMessageTask.getBatchSize()), new ArrayList<>()
        );
        deletePublishedMessages();
        log.debug(String.format("%s Completed publish message task", logPrefix));
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
            log.error(String.format("%s Error encountered during processing of "
                + "unpublished messages", logPrefix), e);
            hasError = true;
        }

        if (!hasError && unpublishedMessagesPaginated.hasNext()) {
            processUnpublishedMessages(unpublishedMessagesPaginated.nextPageable(), processedEntities);
        } else if (!processedEntities.isEmpty()) {
            messageQueueCandidateRepository.saveAll(processedEntities);
            log.info(String.format("%s Published %s messages to destination '%s'",
                                   logPrefix, processedEntities.size(), publishMessageTask.getDestination()
            ));
        }
    }

    private String getPropertyValue(JsonNode data, String propertySourceId) {

        return data.get(propertySourceId).asText();
    }

    private Message setProperties(Message message, JsonNode data) throws JMSException {
        for (MessageProperties property : MessageProperties.values()) {
            if ((data.has(property.getPropertySourceId())) && (!(getPropertyValue(
                data, property.getPropertySourceId()).equals("null")))) {
                message.setStringProperty(property.getPropertyId(), getPropertyValue(
                    data, property.getPropertySourceId())
                );
            }
        }
        return message;
    }

    private void publishMessages(Slice<MessageQueueCandidateEntity> messagesToPublish,
                                 List<MessageQueueCandidateEntity> processedEntities) {
        messagesToPublish.get().forEach(entity -> {
            jmsTemplate.convertAndSend(
                publishMessageTask.getDestination(),
                entity.getMessageInformation(), message -> setProperties(message, entity.getMessageInformation())
            );
            entity.setPublished(LocalDateTime.now());
            processedEntities.add(entity);
        });
    }

    private void deletePublishedMessages() {
        LocalDateTime retentionDate = LocalDateTime.now().minusDays(publishMessageTask.getPublishedRetentionDays());
        int result = messageQueueCandidateRepository
            .deletePublishedMessages(retentionDate, publishMessageTask.getMessageType());
        log.debug(String.format("%s Deleted %s records with publish date before %s",
                                logPrefix, result, retentionDate.toString()
        ));
    }
}
