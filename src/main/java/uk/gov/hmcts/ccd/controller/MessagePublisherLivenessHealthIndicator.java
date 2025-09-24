package uk.gov.hmcts.ccd.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.availability.LivenessStateHealthIndicator;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityState;
import org.springframework.boot.availability.LivenessState;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.hmcts.ccd.data.MessageQueueCandidateEntity;
import uk.gov.hmcts.ccd.data.MessageQueueCandidateRepository;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@SuppressWarnings({"checkstyle:AbbreviationAsWordInName", "checkstyle:MemberName"})
public class MessagePublisherLivenessHealthIndicator extends LivenessStateHealthIndicator {

    @Value("${management.endpoint.health.messageCheckEnvEnabled}")
    private String messageCheckEnvEnabled;

    @Value("${management.endpoint.health.environment}")
    private String environment;

    @Value("${management.endpoint.health.allowedStalePeriod}")
    private int allowedStalePeriod;

    protected static final String ENV_AAT = "aat";

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
        log.debug("CCD MessagePublisher Liveness check configured for environments {} ", messageCheckEnvEnabled);
        if (isNotEnabledForEnvironment(environment)) {
            log.warn("Liveness check is not enabled for the environment {}", environment);
            return LivenessState.CORRECT;
        }

        LocalDateTime currentTime = LocalDateTime.now(clock);
        LocalDateTime utcTimeMinusOneHour = currentTime.minusHours(1);

        log.info("UTC date and time {}, UTC date time minus 1 hour {}, UK local date and time {}",
                 currentTime, utcTimeMinusOneHour, currentTime);

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
            log.info("Message time: {}, Current time: {}, Diff minutes: {}, Time delay: {}",
                     messageTime, currentTime, diffMinutes, allowedStalePeriod
            );
            return diffMinutes > allowedStalePeriod;
        }
        return false;
    }

    public boolean isNotEnabledForEnvironment(String env) {
        log.debug("CCD Message Publisher Liveness check Invoked for environment {} ", env);
        if (ENV_AAT.equals(env)) {
            URI currentUri = ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUri();
            log.info("Invoked API URI: {}", currentUri);
            if (currentUri.toString().contains(STAGING_TEXT)) {
                return true;
            }
        }
        Set<String> envsToEnable = Arrays.stream(messageCheckEnvEnabled.split(","))
            .map(String::trim).collect(Collectors.toSet());
        return !envsToEnable.contains(env);
    }
}
