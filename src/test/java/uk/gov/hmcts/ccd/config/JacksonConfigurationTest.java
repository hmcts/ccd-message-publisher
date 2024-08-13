package uk.gov.hmcts.ccd.config;

import static org.junit.Assert.assertNotNull;
import org.junit.jupiter.api.Test;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MessageConverter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.jms.ConnectionFactory;

public class JacksonConfigurationTest {

    public static JacksonConfiguration jacksonConfiguration = new JacksonConfiguration();

    @Test
    void objectMapperCreates() {
        
        ObjectMapper objectMapper = jacksonConfiguration.defaultObjectMapper();
        assertNotNull(objectMapper);
    }

    @Test
    void connectionFactoryCreates() {
        
        ConnectionFactory factory = jacksonConfiguration.connectionFactory("vm://localhost?broker.persistent=false");

        assertNotNull(factory);
    }

    @Test
    void shouldMapToBytesMessageForActiveMQMessage() {
        
        ConnectionFactory factory = jacksonConfiguration.connectionFactory("vm://localhost?broker.persistent=false");
        JmsTemplate jmsTemplate = jacksonConfiguration.jmsTemplate(factory);

        assertNotNull(jmsTemplate);
    }

    @Test
    void jacksonJmsMessageConverterCreates() {
        
        MessageConverter messageConverter = jacksonConfiguration.jacksonJmsMessageConverter();

        assertNotNull(messageConverter);
    }
    
}
