package topic;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusErrorContext;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * For reading single message from session-enabled subscriptions in Azure Service Bus topics
 */
public class TopicReader {

    private static final Logger logger = LoggerFactory.getLogger(TopicReader.class);

    private static final String CONNECTION_STRING_KEY = "CONNECTION_STRING";
    private static final String TOPIC_KEY = "TOPIC";
    private static final String SUBSCRIPTION_KEY = "SUBSCRIPTION";

    private static final String[] REQUIRED_ENV = { CONNECTION_STRING_KEY, TOPIC_KEY, SUBSCRIPTION_KEY };

    public static void main(String[] args) {
        logger.info("Starting topic reader util");
        verifyEnv();
        readFromTopic();
    }

    private static void verifyEnv() {
        for (String env : REQUIRED_ENV) {
            if (System.getenv(env) == null) {
                logger.error("Environment variable {} is required", env);
                System.exit(1);
            }
        }
    }

    private static void readFromTopic() {
        Consumer<ServiceBusReceivedMessageContext> processMessage = messageContext -> {
            try {
                String body = messageContext.getMessage().getBody().toString();
                logger.info("Received message: " + body);
                messageContext.complete();
                System.exit(0);
            } catch (Exception ex) {
                messageContext.abandon();
            }
        };

        Consumer<ServiceBusErrorContext> processError = errorContext -> {
            logger.error("Failed to retrieve message", errorContext.getException());
        };

        ServiceBusProcessorClient processorClient = new ServiceBusClientBuilder()
            .connectionString(System.getenv(CONNECTION_STRING_KEY))
            .sessionProcessor()
            .topicName(System.getenv(TOPIC_KEY))
            .subscriptionName(System.getenv(SUBSCRIPTION_KEY))
            .processMessage(processMessage)
            .processError(processError)
            .buildProcessorClient();

        processorClient.start();
    }
}
