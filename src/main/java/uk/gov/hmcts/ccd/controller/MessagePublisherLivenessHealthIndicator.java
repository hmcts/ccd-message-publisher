package uk.gov.hmcts.ccd.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.availability.LivenessStateHealthIndicator;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityState;
import org.springframework.boot.availability.LivenessState;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.data.MessageQueueCandidateEntity;
import uk.gov.hmcts.ccd.data.MessageQueueCandidateRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Component
@SuppressWarnings({"checkstyle:AbbreviationAsWordInName", "checkstyle:MemberName"})
public class MessagePublisherLivenessHealthIndicator extends LivenessStateHealthIndicator {

    @Value("${management.endpoint.health.allowedStalePeriod}")
    private int allowedStalePeriod;

    protected static final String STAGING_TEXT = "staging";

    private final MessageQueueCandidateRepository repository;

    private final Clock clock;

    @Autowired
    public MessagePublisherLivenessHealthIndicator(ApplicationAvailability availability,
                                                   MessageQueueCandidateRepository repository,
                                                   Clock clock) {
        super(availability);
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    protected AvailabilityState getState(ApplicationAvailability applicationAvailability) {
        if (hasStaleUnpublishedMessages()) {
            return LivenessState.BROKEN;
        }
        return LivenessState.CORRECT;
    }

    /**
     * Checks if there are stale unpublished messages that exceed the time delay threshold.
     * Returns false if there are no unpublished messages
     * Returns true if the oldest unpublished message
     * is older than the configured time delay, indicating a potential processing issue.
     */
    private boolean hasStaleUnpublishedMessages() {
        Optional<MessageQueueCandidateEntity> messageEntity =
            repository.findFirstByPublishedIsNullOrderByTimeStampAsc();
        if (messageEntity.isPresent()) {
            LocalDateTime currentTime = LocalDateTime.now(clock);
            LocalDateTime messageTime = messageEntity.get().getTimeStamp();
            long diffMinutes = Duration.between(messageTime, currentTime).toMinutes();
            log.debug("Message time: {}, Current time: {}, Diff minutes: {}, Time delay: {}",
                     messageTime, currentTime, diffMinutes, allowedStalePeriod
            );
            return diffMinutes > allowedStalePeriod;
        }
        return false;
    }
}
