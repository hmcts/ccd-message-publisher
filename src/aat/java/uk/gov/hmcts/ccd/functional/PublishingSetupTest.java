package uk.gov.hmcts.ccd.functional;

import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.models.CreateRuleOptions;
import com.azure.messaging.servicebus.administration.models.CreateSubscriptionOptions;
import com.azure.messaging.servicebus.administration.models.TopicProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublishingSetupTest {

    @Test
    void skipsBrokerCleanupWhenSubscriptionCreationFails() throws Exception {
        Object originalPreview = ReflectionTestUtils.getField(PublishingTest.class, "preview");
        Object originalAdministration = ReflectionTestUtils.getField(PublishingTest.class, "administration");
        ServiceBusAdministrationClient administration = mock(ServiceBusAdministrationClient.class);
        PreviewEnvironment preview = mock(PreviewEnvironment.class);
        Connection connection = mock(Connection.class);
        PreparedStatement cleanup = mock(PreparedStatement.class);
        when(preview.topic()).thenReturn("ccd-events");
        when(preview.database()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(cleanup);
        IllegalStateException failure = new IllegalStateException("Subscription creation failed");
        doThrow(failure).when(administration).createSubscription(anyString(), anyString(), anyString(),
            any(CreateSubscriptionOptions.class), any(CreateRuleOptions.class));

        try {
            ReflectionTestUtils.setField(PublishingTest.class, "preview", preview);
            ReflectionTestUtils.setField(PublishingTest.class, "administration", administration);
            PublishingTest scenario = new PublishingTest();
            assertThatThrownBy(scenario::createFilteredSubscription).isSameAs(failure);
            assertThatCode(scenario::cleanUpScenario).doesNotThrowAnyException();
            verify(administration, never()).getSubscriptionExists(anyString(), anyString());
            verify(administration, never()).deleteSubscription(anyString(), anyString());
        } finally {
            ReflectionTestUtils.setField(PublishingTest.class, "preview", originalPreview);
            ReflectionTestUtils.setField(PublishingTest.class, "administration", originalAdministration);
        }
    }

    @Test
    void acceptsTheConfiguredTopic() {
        ServiceBusAdministrationClient administration = mock(ServiceBusAdministrationClient.class, RETURNS_DEEP_STUBS);
        TopicProperties topic = mock(TopicProperties.class);
        when(topic.getName()).thenReturn("ccd-events");
        when(administration.listTopics().stream()).thenReturn(Stream.of(topic));

        assertThatCode(() -> PublishingTest.requireConfiguredTopic(administration, "ccd-events"))
            .doesNotThrowAnyException();
    }

    @Test
    void reportsMissingConfiguredTopicInsteadOfSelectingADifferentOne() {
        ServiceBusAdministrationClient administration = mock(ServiceBusAdministrationClient.class, RETURNS_DEEP_STUBS);
        TopicProperties topic = mock(TopicProperties.class);
        when(topic.getName()).thenReturn("ccd-message-publisher-pr-537-servicebus-ccd-events");
        when(administration.listTopics().stream()).thenReturn(Stream.of(topic));

        assertThatThrownBy(() -> PublishingTest.requireConfiguredTopic(administration, "ccd-events"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Configured publisher topic 'ccd-events' was not found")
            .hasMessageContaining("CCD_CASE_EVENTS_DESTINATION")
            .hasMessageContaining("SERVICE_BUS_CONNECTION_STRING")
            .hasMessageContaining("no candidates were inserted");
    }
}
