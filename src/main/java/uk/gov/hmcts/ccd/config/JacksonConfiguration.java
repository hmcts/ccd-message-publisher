package uk.gov.hmcts.ccd.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;
import com.azure.spring.jms.ServiceBusJmsConnectionFactory;

import jakarta.jms.ConnectionFactory;

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
    @ConditionalOnProperty(name = "spring.jms.servicebus.enabled")
    public ConnectionFactory connectionFactory(
            @Value("${spring.jms.servicebus.connection-string}") String connectionString) {
        return new ServiceBusJmsConnectionFactory(connectionString);
    }

    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory jmsConnectionFactory) {
        final JmsTemplate jmsTemplate = new JmsTemplate();
        jmsTemplate.setMessageConverter(jacksonJmsMessageConverter());
        CachingConnectionFactory cachingConnectionFactory =
            new CachingConnectionFactory(jmsConnectionFactory);
        cachingConnectionFactory.setCacheProducers(false);
        jmsTemplate.setConnectionFactory(cachingConnectionFactory);
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
}
