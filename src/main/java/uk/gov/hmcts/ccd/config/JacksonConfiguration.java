package uk.gov.hmcts.ccd.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.microsoft.azure.spring.autoconfigure.jms.AzureServiceBusJMSProperties;
import com.microsoft.azure.spring.autoconfigure.jms.ConnectionStringResolver;
import com.microsoft.azure.spring.autoconfigure.jms.ServiceBusKey;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

import javax.jms.ConnectionFactory;

@Configuration
public class JacksonConfiguration {

    @Bean
    public ObjectMapper defaultObjectMapper() {
        return new ObjectMapper()
            .registerModule(new Jdk8Module())
            .registerModule(new ParameterNamesModule(JsonCreator.Mode.PROPERTIES))
            .registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory jmsConnectionFactory) {
        final JmsTemplate jmsTemplate = new JmsTemplate();
        jmsTemplate.setMessageConverter(jacksonJmsMessageConverter());
        jmsTemplate.setConnectionFactory(jmsConnectionFactory);
        return jmsTemplate;
    }

    @Bean
    public MessageConverter jacksonJmsMessageConverter() {
        MappingJackson2MessageConverter converter = new CcdMessageConverter();
        converter.setObjectMapper(defaultObjectMapper());
        converter.setTargetType(MessageType.BYTES);
        converter.setTypeIdPropertyName("_type");
        return converter;
    }

    @Bean
    public ConnectionFactory jmsConnectionFactory(AzureServiceBusJMSProperties busJMSProperties) {
        final String connectionString = busJMSProperties.getConnectionString();
        final String clientId = busJMSProperties.getTopicClientId();
        final int idleTimeout = busJMSProperties.getIdleTimeout();

        final ServiceBusKey serviceBusKey = ConnectionStringResolver.getServiceBusKey(connectionString);

        final String remoteUri = String.format("amqps://%s?amqp.idleTimeout=%d&amqp.traceFrames=true",
            serviceBusKey.getHost(), idleTimeout);

        final JmsConnectionFactory jmsConnectionFactory =
            new JmsConnectionFactory(
                serviceBusKey.getSharedAccessKeyName(),
                serviceBusKey.getSharedAccessKey(),
                remoteUri
            );
        jmsConnectionFactory.setClientID(clientId);

        CachingConnectionFactory cachingConnectionFactory =
            new CachingConnectionFactory(jmsConnectionFactory);
        // set cache producers to FALSE here
        cachingConnectionFactory.setCacheProducers(false);

        return cachingConnectionFactory;
    }
}
