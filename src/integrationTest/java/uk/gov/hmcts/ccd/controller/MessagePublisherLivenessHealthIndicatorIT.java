package uk.gov.hmcts.ccd.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityState;
import org.springframework.boot.availability.LivenessState;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.ccd.BaseTest;
import uk.gov.hmcts.ccd.data.MessageQueueCandidateEntity;
import uk.gov.hmcts.ccd.data.MessageQueueCandidateRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
@DirtiesContext
class MessagePublisherLivenessHealthIndicatorIT extends BaseTest {

    private static final String CLEANUP_SCRIPT = "classpath:sql/cleanup-message-queue-candidates.sql";

    @Autowired
    private MessageQueueCandidateRepository repository;

    @Autowired
    private Clock clock;

    @Autowired
    private ApplicationAvailability applicationAvailability;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MessagePublisherLivenessHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(healthIndicator, "allowedStalePeriod", 5);
    }

    @Test
    void shouldHaveHealthIndicatorBeanRegistered() {
        // Given & When
        boolean hasBean = applicationContext.containsBean("messagePublisherLivenessHealthIndicator");

        // Then
        assertTrue(hasBean, "messagePublisherLivenessHealthIndicator bean should be registered in Spring context");
    }

    @Test
    @Sql(scripts = {CLEANUP_SCRIPT}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @DirtiesContext
    void shouldReturnCorrectWhenNoMessagesExist() {
        // Given - no messages in database

        // When
        AvailabilityState result = healthIndicator.getState(applicationAvailability);

        // Then
        assertEquals(LivenessState.CORRECT, result);
    }

    @Test
    @Sql(scripts = {CLEANUP_SCRIPT}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @DirtiesContext
    void shouldReturnCorrectWhenMessagesAreWithinAllowedStalePeriod() {
        // Given - create messages that are within the time delay
        MessageQueueCandidateEntity recentMessage = createMessageEntity(
            LocalDateTime.now().minusMinutes(2), // Within 5-minute delay
            "RECENT_MESSAGE",
            "{\"test\": \"recent\"}"
        );

        repository.saveAll(Arrays.asList(recentMessage));

        // When
        AvailabilityState result = healthIndicator.getState(applicationAvailability);

        // Then
        assertEquals(LivenessState.CORRECT, result);
    }

    @Test
    @Sql(scripts = {CLEANUP_SCRIPT}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @DirtiesContext
    void shouldReturnBrokenWhenOldestMessageIsBeyondAllowedStalePeriod() {
        // Given - create messages where the oldest is beyond the time delay
        MessageQueueCandidateEntity oldMessage = createMessageEntity(
            LocalDateTime.now().minusMinutes(10), // Beyond 5-minute delay
            "OLD_MESSAGE",
            "{\"test\": \"old\"}"
        );
        MessageQueueCandidateEntity recentMessage = createMessageEntity(
            LocalDateTime.now().minusMinutes(2), // Within 5-minute delay
            "RECENT_MESSAGE",
            "{\"test\": \"recent\"}"
        );

        repository.saveAll(Arrays.asList(oldMessage, recentMessage));

        // When
        AvailabilityState result = healthIndicator.getState(applicationAvailability);

        // Then
        assertEquals(LivenessState.BROKEN, result);
    }

    @Test
    @Sql(scripts = {CLEANUP_SCRIPT}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @DirtiesContext
    void shouldReturnCorrectWhenAllMessagesArePublished() {
        // Given - create messages that are all published (should still return BROKEN
        // because no unpublished messages)
        MessageQueueCandidateEntity publishedMessage1 = createMessageEntity(
            LocalDateTime.now().minusMinutes(10),
            "PUBLISHED_MESSAGE_1",
            "{\"test\": \"published1\"}"
        );
        publishedMessage1.setPublished(LocalDateTime.now().minusMinutes(5));

        MessageQueueCandidateEntity publishedMessage2 = createMessageEntity(
            LocalDateTime.now().minusMinutes(8),
            "PUBLISHED_MESSAGE_2",
            "{\"test\": \"published2\"}"
        );
        publishedMessage2.setPublished(LocalDateTime.now().minusMinutes(3));

        repository.saveAll(Arrays.asList(publishedMessage1, publishedMessage2));

        // When
        AvailabilityState result = healthIndicator.getState(applicationAvailability);

        // Then - should return CORRECT because there are no unpublished messages
        assertEquals(LivenessState.CORRECT, result);
    }

    @Test
    @Sql(scripts = {CLEANUP_SCRIPT}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @DirtiesContext
    void shouldReturnCorrectWhenUnpublishedMessageIsRecent() {
        // Given - create only an unpublished message that is very recent
        MessageQueueCandidateEntity unpublishedMessage = createMessageEntity(
            LocalDateTime.now().minusMinutes(1), // Very recent - well within 5 minute delay
            "UNPUBLISHED_MESSAGE",
            "{\"test\": \"unpublished\"}"
        );
        // published is null

        repository.saveAll(Arrays.asList(unpublishedMessage));

        // When
        AvailabilityState result = healthIndicator.getState(applicationAvailability);

        // Then - should return CORRECT because the unpublished message is recent (within 5 minutes)
        assertEquals(LivenessState.CORRECT, result);
    }

    @Test
    @Sql(scripts = {CLEANUP_SCRIPT}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @DirtiesContext
    void shouldReturnCorrectWhenMessageIsExactlyAtAllowedStalePeriod() {
        // Given - create a message that is exactly at the time delay boundary
        MessageQueueCandidateEntity message = createMessageEntity(
            LocalDateTime.now().minusMinutes(5), // Exactly at 5-minute delay
            "BOUNDARY_MESSAGE",
            "{\"test\": \"boundary\"}"
        );

        repository.saveAll(Arrays.asList(message));

        // When
        AvailabilityState result = healthIndicator.getState(applicationAvailability);

        // Then
        assertEquals(LivenessState.CORRECT, result);
    }

    private MessageQueueCandidateEntity createMessageEntity(LocalDateTime timestamp,
                                                            String messageType,
                                                            String messageInfo) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode messageInformation = objectMapper.readTree(messageInfo);

            MessageQueueCandidateEntity entity = new MessageQueueCandidateEntity();
            entity.setTimeStamp(timestamp);
            entity.setMessageType(messageType);
            entity.setMessageInformation(messageInformation);
            return entity;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test message entity", e);
        }
    }
}
