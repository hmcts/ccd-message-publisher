package uk.gov.hmcts.ccd.messagepublisher.befta.publishing;

import com.azure.core.amqp.AmqpRetryOptions;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.ServiceBusSessionReceiverClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.Before;
import uk.gov.hmcts.befta.BeftaMain;
import uk.gov.hmcts.befta.data.ResponseData;
import uk.gov.hmcts.ccd.messagepublisher.befta.MessagePublisherTestAutomationAdapter;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** BEFTA assertions for delivery of the case created by the current scenario. */
public class PublishingSteps {

    private PreviewPublishingConfiguration config;

    @Before("@Publishing")
    public void prepareScenario() {
        ((MessagePublisherTestAutomationAdapter) BeftaMain.getAdapter()).resetPublishingScenario();
    }

    @Given("the publishing preview has isolated routing and a dedicated subscription")
    public void verifyPreviewConfiguration() {
        config = new PreviewPublishingConfiguration(System.getenv());
    }

    @Then("the created case event is delivered to the dedicated preview subscription")
    public void verifyPublishedCaseEvent() throws Exception {
        MessagePublisherTestAutomationAdapter adapter =
            (MessagePublisherTestAutomationAdapter) BeftaMain.getAdapter();
        ResponseData response = adapter.publishingContext().getTheResponse();
        assertEquals(201, response.getResponseCode(), "BEFTA must create a new case");
        String caseId = String.valueOf(response.getBody().get("id"));
        assertTrue(caseId.matches("[0-9]+"), "Case creation must return a case reference");
        System.out.println("Publishing E2E created case " + caseId + " with marker " + adapter.publishingMarker());

        try (ServiceBusSessionReceiverClient sessions = new ServiceBusClientBuilder()
            .connectionString(config.required("PUBLISHING_E2E_CONNECTION_STRING"))
            .retryOptions(new AmqpRetryOptions().setTryTimeout(Duration.ofSeconds(20)).setMaxRetries(1))
            .sessionReceiver().topicName(config.required("PUBLISHING_E2E_TOPIC"))
            .subscriptionName(config.required("PUBLISHING_E2E_SUBSCRIPTION"))
            .disableAutoComplete().buildClient();
            ServiceBusReceiverClient receiver = sessions.acceptSession(caseId)) {
            ServiceBusReceivedMessage message = receiver.receiveMessages(1, Duration.ofSeconds(60))
                .stream().findFirst().orElseThrow(() -> new AssertionError(
                    "No published message received for case " + caseId + " within 60 seconds"));
            try {
                JsonNode body = new ObjectMapper().readTree(message.getBody().toString());
                assertEquals(caseId, body.path("CaseId").asText());
                assertEquals("FT_MultiplePages", body.path("CaseTypeId").asText());
                assertEquals("BEFTA_MASTER", body.path("JurisdictionId").asText());
                assertEquals("createCase", body.path("EventId").asText());
                assertEquals(adapter.publishingMarker(), body.path("AdditionalData").path("Data")
                    .path("text_field_last_name").asText());
                assertEquals(caseId, message.getSessionId());
                assertEquals(caseId, message.getApplicationProperties().get("case_id"));
                assertEquals("createCase", message.getApplicationProperties().get("event_id"));
                receiver.complete(message);
            } catch (Exception | AssertionError failure) {
                try {
                    receiver.abandon(message);
                } catch (RuntimeException abandonFailure) {
                    if (abandonFailure != failure) {
                        failure.addSuppressed(abandonFailure);
                    }
                }
                throw failure;
            }
        }
    }
}
