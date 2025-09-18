package uk.gov.hmcts.ccd.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityState;
import org.springframework.boot.availability.LivenessState;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.ccd.data.MessageQueueCandidateEntity;
import uk.gov.hmcts.ccd.data.MessageQueueCandidateRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessagePublisherLivenessHealthIndicatorTest {

    private static final int TIME_DELAY = 5; // Default time delay from @Value

    @Mock
    private ApplicationAvailability applicationAvailability;

    @Mock
    private MessageQueueCandidateRepository repository;

    @Mock
    private Clock clock;

    private MessagePublisherLivenessHealthIndicator healthIndicator;

    @BeforeEach
    void setUp() {
        healthIndicator = new MessagePublisherLivenessHealthIndicator(
            applicationAvailability, repository, clock);
        ReflectionTestUtils.setField(healthIndicator, "allowedStalePeriod", TIME_DELAY);
        ReflectionTestUtils.setField(healthIndicator, "messageCheckEnvEnabled", "prod,aat");
        ReflectionTestUtils.setField(healthIndicator, "environment", "prod");
    }

    @Test
    void shouldReturnCorrectWhenEnvironmentIsDisabled() {
        // Given
        ReflectionTestUtils.setField(healthIndicator, "environment", "dev"); // Not in enabled list

        // When
        AvailabilityState result = healthIndicator.getState(applicationAvailability);

        // Then
        assertEquals(LivenessState.CORRECT, result);
    }

    @Test
    void shouldReturnCorrectWhenEnvironmentIsEnabledAndNoMessages() {
        // Given
        when(repository.findFirstByPublishedIsNullOrderByTimeStampAsc()).thenReturn(Optional.empty());
        when(clock.instant()).thenReturn(Instant.now());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        // When
        AvailabilityState result = healthIndicator.getState(applicationAvailability);

        // Then
        assertEquals(LivenessState.CORRECT, result);
    }

    @Test
    void shouldReturnCorrectWhenMessageIsWithinAllowedStalePeriod() {
        // Given
        LocalDateTime messageTime = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(2); // Within 5-minute delay
        when(repository.findFirstByPublishedIsNullOrderByTimeStampAsc())
            .thenReturn(Optional.of(createMessageEntity(messageTime)));
        when(clock.instant()).thenReturn(Instant.now());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        // When
        AvailabilityState result = healthIndicator.getState(applicationAvailability);

        // Then
        assertEquals(LivenessState.CORRECT, result);
    }

    @Test
    void shouldReturnBrokenWhenMessageIsBeyondAllowedStalePeriod() {
        // Given
        // Beyond 5-minute delay
        LocalDateTime messageTime = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(TIME_DELAY + 1);
        when(repository.findFirstByPublishedIsNullOrderByTimeStampAsc())
            .thenReturn(Optional.of(createMessageEntity(messageTime)));
        when(clock.instant()).thenReturn(Instant.now());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        // When
        AvailabilityState result = healthIndicator.getState(applicationAvailability);

        // Then
        assertEquals(LivenessState.BROKEN, result);
    }

    @Test
    void shouldReturnCorrectWhenNoMessagesExist() {
        // Given
        when(repository.findFirstByPublishedIsNullOrderByTimeStampAsc()).thenReturn(Optional.empty());
        when(clock.instant()).thenReturn(Instant.now());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        // When
        AvailabilityState result = healthIndicator.getState(applicationAvailability);

        // Then
        assertEquals(LivenessState.CORRECT, result);
    }

    @Test
    void shouldReturnCorrectWhenEnvironmentIsInEnabledList() {
        // Given
        ReflectionTestUtils.setField(healthIndicator, "environment", "prod");
        ReflectionTestUtils.setField(healthIndicator, "messageCheckEnvEnabled", "prod,aat,test");
        when(repository.findFirstByPublishedIsNullOrderByTimeStampAsc()).thenReturn(Optional.empty());
        when(clock.instant()).thenReturn(Instant.now());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        // When
        AvailabilityState result = healthIndicator.getState(applicationAvailability);

        // Then
        assertEquals(LivenessState.CORRECT, result);
    }

    @Test
    void shouldReturnCorrectWhenEnvironmentIsNotInEnabledList() {
        // Given
        ReflectionTestUtils.setField(healthIndicator, "environment", "dev");
        ReflectionTestUtils.setField(healthIndicator, "messageCheckEnvEnabled", "prod,aat,test");

        // When
        AvailabilityState result = healthIndicator.getState(applicationAvailability);

        // Then
        assertEquals(LivenessState.CORRECT, result);
    }

    @Test
    void shouldReturnCorrectWhenMessageIsExactlyAtAllowedStalePeriod() {
        // Given
        LocalDateTime messageTime = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(TIME_DELAY);
        when(repository.findFirstByPublishedIsNullOrderByTimeStampAsc())
            .thenReturn(Optional.of(createMessageEntity(messageTime)));
        when(clock.instant()).thenReturn(Instant.now());
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        // When
        AvailabilityState result = healthIndicator.getState(applicationAvailability);

        // Then
        assertEquals(LivenessState.CORRECT, result);
    }

    // Note: AAT environment test with staging URI check is skipped in unit tests
    // because it requires ServletUriComponentsBuilder which needs a request context.
    // This functionality is tested in integration tests where the full web context is available.

    @Test
    void isNotEnabledForEnvironmentShouldReturnFalseForEnabledEnvironment() {
        // Given
        String prodEnv = "prod";
        ReflectionTestUtils.setField(healthIndicator, "environment", prodEnv);
        ReflectionTestUtils.setField(healthIndicator, "messageCheckEnvEnabled", "prod,aat");

        // When
        boolean result = healthIndicator.isNotEnabledForEnvironment(prodEnv);

        // Then
        assertFalse(result);
    }

    @Test
    void isNotEnabledForEnvironmentShouldReturnTrueForDisabledEnvironment() {
        // Given
        String devEnv = "dev";
        ReflectionTestUtils.setField(healthIndicator, "environment", devEnv);
        ReflectionTestUtils.setField(healthIndicator, "messageCheckEnvEnabled", "prod,aat");

        // When
        boolean result = healthIndicator.isNotEnabledForEnvironment(devEnv);

        // Then
        assertTrue(result);
    }

    private MessageQueueCandidateEntity createMessageEntity(LocalDateTime timestamp) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode messageInformation = objectMapper.readTree("{\"test\": \"message\"}");

            MessageQueueCandidateEntity entity = new MessageQueueCandidateEntity();
            entity.setTimeStamp(timestamp);
            entity.setMessageType("TEST_MESSAGE");
            entity.setMessageInformation(messageInformation);
            return entity;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test message entity", e);
        }
    }
}
